package com.smk.shop;

import org.apache.catalina.Context;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;

import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        String webappDirLocation = "src/main/webapp/";
        Tomcat tomcat = new Tomcat();

        String webPort = System.getenv("PORT");
        if (webPort == null || webPort.isEmpty()) {
            webPort = "8080";
        }

        tomcat.setPort(Integer.parseInt(webPort));
        tomcat.getConnector(); // Force creation of default connector

        Context ctx = tomcat.addWebapp("/", new File(webappDirLocation).getAbsolutePath());
        ctx.setParentClassLoader(Main.class.getClassLoader()); // Fix ClassNotFoundException for dependencies
        System.out.println("Configuring app with basedir: " + new File(webappDirLocation).getAbsolutePath());

        // Configure classes directory
        File additionWebInfClasses = new File("target/classes");
        WebResourceRoot resources = new StandardRoot(ctx);
        resources.addPreResources(new DirResourceSet(resources, "/WEB-INF/classes",
                additionWebInfClasses.getAbsolutePath(), "/"));
        ctx.setResources(resources);

        tomcat.start();
        System.out.println("Embedded Tomcat started on port " + webPort);
        tomcat.getServer().await();
    }
}
