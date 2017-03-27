package com.cjd96.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @author lugl
 *         create 2017-03-27-17:31
 */
public class SpringContextHelper implements ApplicationContextAware {
    private static ApplicationContext applicationContext = null;
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    public static Object getBean(String name){
        return applicationContext.getBean(name);
    }
}
