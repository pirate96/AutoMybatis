package com.cjd96.tool;

import com.cjd96.annotation.Table;
import com.cjd96.config.SpringConfig;
import com.cjd96.config.SpringContextHelper;
import com.cjd96.entityBase.IEntity;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * @author lugl
 *         create 2017-01-12-17:44
 */
public class AutoBuildDB {

    private String classPackage = "com.cjd96.entities";

    private String mapperPath = System.getProperty("user.dir") + File.separator +"AutoMybatis"+ File.separator + "src" + File.separator + "main"
            + File.separator + "resources" + File.separator + "mappers" + File.separator;

    public static void main(String[] args) {
        AbstractApplicationContext ctx = new AnnotationConfigApplicationContext(SpringConfig.class);
        ctx.registerShutdownHook();
        AutoBuildDB autoBuildDB = new AutoBuildDB();
        autoBuildDB.checkDB();
    }

    public void checkDB() {
        JdbcTemplate jdbcTemplate = (JdbcTemplate) SpringContextHelper.getBean("jdbcTemplate");
        try {
            Connection connection = jdbcTemplate.getDataSource().getConnection();
            List<Class> clazzs = getClasssFromPackage(classPackage);
            for (Class clazz : clazzs) {
                // 处理xml部分
                dealMapper(clazz);
                Table table = (Table) clazz.getAnnotation(Table.class);
                if (table == null) {
                    continue;
                }
                Field[] fields = clazz.getDeclaredFields();
                // 处理数据库部分
                if (!hadTable(connection, table.tableName())) {
                    // 不存在,创建新表
                    createTable(jdbcTemplate, table, fields);
                } else {
                    Map<String, ColumnInfo> map = getColumnInfoMap(connection, table.tableName());
                    for (Field field : fields) {
                        if (Modifier.isTransient(field.getModifiers())) {
                            continue;
                        }
                        if (!map.containsKey(field.getName())) {
                            addColumn(jdbcTemplate, table, field);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean dealMapper(Class<? extends IEntity> clazz) {
        String fileName = clazz.getSimpleName() + "Mapper.xml";
        String filePath = mapperPath + fileName;
        System.out.println(filePath);
        File file = new File(filePath);
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.write(getCreateString(clazz).getBytes("UTF-8"));
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getCreateString(Class<? extends IEntity> clazz) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<!DOCTYPE mapper\n" +
                "        PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n" +
                "        \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n");
        String namespace = clazz.getName();
        stringBuilder.append("<mapper namespace=\"").append(namespace).append("\">\n");
        try {
            stringBuilder.append(getDefaultIEntityString(clazz));
        } catch (Exception e) {
            e.printStackTrace();
        }
        // end
        stringBuilder.append("</mapper>");
        return stringBuilder.toString();
    }

    public String getDefaultIEntityString(Class<? extends IEntity> clazz) throws Exception {
        Table table = clazz.getAnnotation(Table.class);
        Field keyField = clazz.getDeclaredField(table.keyField());
        if (keyField == null) {
            throw new Exception("table " + table.tableName() + " need a primary key!!!");
        }
        Field[] fields = clazz.getDeclaredFields();
        StringBuilder stringBuilder = new StringBuilder();
        // insert
        stringBuilder.append("    <insert id=\"insert\" parameterType=\"").append("map").append("\"");
        if (table.isAutoInc()) {
            stringBuilder.append(" useGeneratedKeys=\"true\" keyProperty=\"").append(keyField.getName()).append("\">\n");
            stringBuilder.append("        INSERT INTO ").append(table.tableName()).append(" (");
            StringBuilder stringBuilder1 = new StringBuilder();
            StringBuilder stringBuilder2 = new StringBuilder();
            for (Field field : fields) {
                if (Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                if (!field.getName().equals(keyField.getName())) {
                    stringBuilder1.append(field.getName()).append(",");
                    stringBuilder2.append("#{").append(field.getName()).append("},");
                }
            }
            stringBuilder1.deleteCharAt(stringBuilder1.length() - 1);
            stringBuilder2.deleteCharAt(stringBuilder2.length() - 1);
            stringBuilder.append(stringBuilder1.toString()).append(") VALUES (").append(stringBuilder2.toString()).append(")\n");
            stringBuilder.append("        <selectKey resultType=\"").append(keyField.getType().getSimpleName()).append("\" keyProperty=\"").append(keyField.getName()).append("\">\n");
            stringBuilder.append("            SELECT last_insert_id() AS ").append(keyField.getName()).append(" FROM ").append(table.tableName());
            stringBuilder.append(" LIMIT 1;\n" +
                    "        </selectKey>\n" +
                    "    </insert>\n");
        } else {
            stringBuilder.append(">\n");
            stringBuilder.append("        INSERT INTO ").append(table.tableName()).append(" (");
            StringBuilder stringBuilder1 = new StringBuilder();
            StringBuilder stringBuilder2 = new StringBuilder();
            for (Field field : fields) {
                if (Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                stringBuilder1.append(field.getName()).append(",");
                stringBuilder2.append("#{").append(field.getName()).append("},");
            }
            stringBuilder1.deleteCharAt(stringBuilder1.length() - 1);
            stringBuilder2.deleteCharAt(stringBuilder2.length() - 1);
            stringBuilder.append(stringBuilder1.toString()).append(") VALUES (").append(stringBuilder2.toString()).append(")\n");
            stringBuilder.append("    </insert>\n");
        }
        // update
        stringBuilder.append("    <update id=\"update\" parameterType=\"").append("map").append("\">\n");
        stringBuilder.append("        UPDATE ").append(table.tableName()).append("\n");
        stringBuilder.append("        SET");
        for (Field field : fields) {
            if (Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            if (field.getName().equals(keyField.getName())) {
                continue;
            }
            stringBuilder.append(" ").append(field.getName()).append(" = #{").append(field.getName()).append("},");
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        stringBuilder.append("\n" +
                "        WHERE ").append(keyField.getName()).append(" = #{").append(keyField.getName()).append("}\n");
        stringBuilder.append("    </update>\n");
        // select
        stringBuilder.append("    <select id=\"select\" parameterType=\"").append("object").append("\" resultType=\"map\">\n");
        stringBuilder.append("        SELECT *\n");
        stringBuilder.append("        FROM ").append(table.tableName()).append("\n");
        stringBuilder.append("        WHERE ").append(keyField.getName()).append(" = #{").append(keyField.getName()).append("}\n");
        stringBuilder.append("    </select>\n");
        // delete
        stringBuilder.append("    <delete id=\"delete\" parameterType=\"").append("object").append("\">\n");
        stringBuilder.append("        DELETE \n");
        stringBuilder.append("        FROM ").append(table.tableName()).append("\n");
        stringBuilder.append("        WHERE ").append(keyField.getName()).append(" = #{").append(keyField.getName()).append("}\n");
        stringBuilder.append("    </delete>\n");
        return stringBuilder.toString();
    }


    /**
     * 是否存在目标表
     *
     * @param connection 数据库连接
     * @param tableName  表名
     * @return 是否存在
     */
    private static boolean hadTable(Connection connection, String tableName) {
        try {
            ResultSet resultSet = connection.getMetaData().getColumns(null, null, tableName, null);
            if (resultSet.next()) {
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 创建表
     *
     * @param jdbcTemplate jt
     * @param table        表
     * @param fields       字段
     */
    private static void createTable(JdbcTemplate jdbcTemplate, Table table, Field[] fields) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("CREATE TABLE `").append(table.tableName()).append("`(");
        for (Field field : fields) {
            if (Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            if (table.isAutoInc() && field.getName().equals(table.keyField())) {
                stringBuilder.append("`").append(field.getName()).append("` ").append(getSqlTypeByField(field)).append(" AUTO_INCREMENT").append(",");
            } else {
                stringBuilder.append("`").append(field.getName()).append("` ").append(getSqlTypeByField(field)).append(",");
            }
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        stringBuilder.append(",PRIMARY KEY (").append(table.keyField()).append(")");
        stringBuilder.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8;");
        System.out.println(stringBuilder.toString());
        jdbcTemplate.update(stringBuilder.toString());
    }

    /**
     * 获取表的所有字段信息
     *
     * @param connection 数据库连接
     * @param tableName  表名
     * @return 字段信息
     */
    private static Map<String, ColumnInfo> getColumnInfoMap(Connection connection, String tableName) {
        Map<String, ColumnInfo> map = new HashMap<>();
        try {
            ResultSet resultSet = connection.getMetaData().getColumns(null, null, tableName, null);
            while (resultSet.next()) {
                ColumnInfo columnInfo = new ColumnInfo();
                //获得字段名称
                columnInfo.name = resultSet.getString("COLUMN_NAME");
                //获得字段类型名称
                columnInfo.type = resultSet.getString("TYPE_NAME");
                map.put(columnInfo.name, columnInfo);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    /**
     * 添加字段
     *
     * @param jdbcTemplate jt
     * @param table        表名
     * @param field        字段
     */
    private static void addColumn(JdbcTemplate jdbcTemplate, Table table, Field field) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ALTER TABLE `").append(table.tableName()).append("` ADD `").append(field.getName()).append("` ").append(getSqlTypeByField(field)).append(";");
        jdbcTemplate.update(stringBuilder.toString());
    }

    private static String getSqlTypeByField(Field field) {
        if (field.getType() == long.class || field.getType() == Long.class) {
            return "BIGINT(" + 20 + ")";
        } else if (field.getType() == int.class || field.getType() == Integer.class) {
            return "INT(" + 11 + ")";
        } else if (field.getType() == short.class || field.getType() == Short.class) {
            return "SMALLINT(" + 6 + ")";
        } else if (field.getType() == byte.class || field.getType() == Byte.class) {
            return "TINYINT(" + 4 + ")";
        } else if (field.getType() == double.class || field.getType() == Double.class) {
            return "DOUBLE";
        } else if (field.getType() == float.class || field.getType() == Float.class) {
            return "FLOAT";
        } else if (field.getType() == String.class) {
            return "VARCHAR(255)";
        } else if (field.getType() == boolean.class || field.getType() == Boolean.class) {
            return "TINYINT(1)";
        } else {
            return "TEXT";
        }
    }

    /**
     * 获得包下面的所有的class
     *
     * @param packageName package完整名称
     * @return List包含所有class的实例
     */
    private static List<Class> getClasssFromPackage(String packageName) {
        List<Class> clazzs = new ArrayList<>();
        // 是否循环搜索子包
        boolean recursive = true;
        // 包名对应的路径名称
        String packageDirName = packageName.replace('.', '/');
        Enumeration<URL> dirs;
        try {
            dirs = Thread.currentThread().getContextClassLoader().getResources(packageDirName);
            while (dirs.hasMoreElements()) {
                URL url = dirs.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    System.out.println("file类型的扫描");
                    String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
                    findClassInPackageByFile(packageName, filePath, recursive, clazzs);
                } else if ("jar".equals(protocol)) {
                    System.out.println("jar类型的扫描");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return clazzs;
    }

    /**
     * 在package对应的路径下找到所有的class
     *
     * @param packageName package名称
     * @param filePath    package对应的路径
     * @param recursive   是否查找子package
     * @param clazzs      找到class以后存放的集合
     */
    private static void findClassInPackageByFile(String packageName, String filePath, final boolean recursive, List<Class> clazzs) {
        File dir = new File(filePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        // 在给定的目录下找到所有的文件，并且进行条件过滤
        File[] dirFiles = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                boolean acceptDir = recursive && file.isDirectory();// 接受dir目录
                boolean acceptClass = file.getName().endsWith("class");// 接受class文件
                return acceptDir || acceptClass;
            }
        });
        for (File file : dirFiles) {
            if (file.isDirectory()) {
                findClassInPackageByFile(packageName + "." + file.getName(), file.getAbsolutePath(), recursive, clazzs);
            } else {
                String className = file.getName().substring(0, file.getName().length() - 6);
                try {
                    clazzs.add(Thread.currentThread().getContextClassLoader().loadClass(packageName + "." + className));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

class ColumnInfo {
    public String name;
    public String type;

    @Override
    public String toString() {
        return "ColumnInfo{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
