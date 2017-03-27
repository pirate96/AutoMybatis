package com.cjd96.entities;

import com.cjd96.annotation.Table;
import com.cjd96.entityBase.IEntity;

/**
 * @author lugl
 *         create 2017-03-27-17:14
 */
@Table(tableName = "tb_test", keyField = "id", isAutoInc = true)
public class TestEntity implements IEntity {
    private long id;
    private String name;
    private String pwd;
    private int age;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "TestEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", pwd='" + pwd + '\'' +
                ", age=" + age +
                '}';
    }
}
