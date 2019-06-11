package com.jxh.tool;

import lombok.extern.apachecommons.CommonsLog;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.dom.DOMElement;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * 通过Jar包SHA1或MD5生成Pom文件
 *
 * @author JiaXiaohei
 * @date 2018-03-16
 */
@CommonsLog
public class Jar2pom {

    /**
     * maven库查询地址
     */
    private static final String NEXUS_URL = "http://repo.thunisoft.com/maven2/service/local/lucene/search";

    /**
     * jar地址
     */
    private static final String LIB_PATH = "E:\\project\\广西高院审委会项目\\shxt\\web\\WEB-INF\\lib";

    public static void main(String[] args) {
        StringBuilder builder = new StringBuilder();
        // 先通过Jar的SHA1查询 如果不存在则解析Manifest查询
        File libPath = new File(LIB_PATH);
        File[] libs = libPath.listFiles();
        if (libs == null) {
            return;
        }
        for (File jar : libs) {
            builder.append("<!--  ").append(jar.getName()).append(" -->\n");
            if (!getPomByChecksum(jar).isTextOnly()) {
                builder.append("<!--  Search by Checksum -->\n");
                builder.append(getPomByChecksum(jar).asXML());
            } else if (!getPomByManifest(jar).isTextOnly()) {
                builder.append("<!--  Search by Manifest -->\n");
                builder.append(getPomByManifest(jar).asXML());
            } else {
                builder.append("<!--  No data was found -->");
            }
            builder.append("\n");
        }
        log.info("pom信息：\n" + builder);
    }

    /**
     * 通过Jar SHA1返回Pom dependency
     *
     * @param file jar文件
     * @return xml元素
     */
    private static Element getPomByChecksum(File file) {
        String checkSum = getCheckSum(file, "SHA1");
        String xml = doGet(NEXUS_URL + "?sha1=" + checkSum);
        return assemblePomElement(xml);
    }

    /**
     * 通过Jar Manifest返回Pom dependency
     *
     * @param file jar文件
     * @return xml元素
     */
    private static Element getPomByManifest(File file) {
        try (JarFile jarfile = new JarFile(file)) {
            Manifest manifest = jarfile.getManifest();
            if (null == manifest) {
                return createDependency();
            }
            String a = null;
            if (manifest.getMainAttributes().containsKey(new Attributes.Name("Extension-Name"))) {
                a = manifest.getMainAttributes().getValue(new Attributes.Name("Extension-Name"));
            } else if (manifest.getMainAttributes().containsKey(new Attributes.Name("Implementation-Title"))) {
                a = manifest.getMainAttributes().getValue(new Attributes.Name("Implementation-Title"));
            } else if (manifest.getMainAttributes().containsKey(new Attributes.Name("Specification-Title"))) {
                a = manifest.getMainAttributes().getValue(new Attributes.Name("Specification-Title"));
            }
            if (StringUtils.isNotBlank(a)) {
                a = a.replace("\"", "").replace(" ", "-");
            }
            String v = null;
            if (manifest.getMainAttributes().containsKey(new Attributes.Name("Bundle-Version"))) {
                v = manifest.getMainAttributes().getValue(new Attributes.Name("Bundle-Version"));
            } else if (manifest.getMainAttributes().containsKey(new Attributes.Name("Implementation-Version"))) {
                v = manifest.getMainAttributes().getValue(new Attributes.Name("Implementation-Version"));
            } else if (manifest.getMainAttributes().containsKey(new Attributes.Name("Specification-Version"))) {
                v = manifest.getMainAttributes().getValue(new Attributes.Name("Specification-Version"));
            }
            if (StringUtils.isNotBlank(v)) {
                v = v.replace("\"", "").replace(" ", "-");
            }
            String xml = doGet(NEXUS_URL + "?a=" + a + "&v=" + v);
            return assemblePomElement(xml);
        } catch (IOException e) {
            log.error(e);
        }
        return createDependency();
    }

    /**
     * 解析获取的XML 组装dependency
     *
     * @param xml 字符串
     * @return xml元素
     */
    private static Element assemblePomElement(String xml) {
        Element dependency = createDependency();

        if (StringUtils.isNotBlank(xml)) {
            try {
                Document document = DocumentHelper.parseText(xml);
                Element dataElement = document.getRootElement().element("data");
                String text = dataElement.getText();
                if (StringUtils.isNotBlank(text)) {
                    Element artifactElement = dataElement.element("artifact");
                    dependency.add((Element) artifactElement.element("groupId").clone());
                    dependency.add((Element) artifactElement.element("artifactId").clone());
                    dependency.add((Element) artifactElement.element("version").clone());
                }
            } catch (DocumentException e) {
                log.error(e);
            }
        }
        return dependency;
    }

    /**
     * 发起Get请求
     *
     * @param url 请求地址
     * @return 返回结果
     */
    private static String doGet(String url) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);
            HttpResponse httpResponse = httpClient.execute(httpGet);
            return EntityUtils.toString(httpResponse.getEntity());
        } catch (IOException e) {
            log.error(e);
        }
        return null;
    }

    /**
     * 计算CheckSum
     *
     * @param file      文件
     * @param algorithm SHA1 or MD5
     * @return CheckSum
     */
    private static String getCheckSum(File file, String algorithm) {
        if (!file.isFile()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer, 0, 1024)) != -1) {
                digest.update(buffer, 0, len);
            }
            BigInteger bigInt = new BigInteger(1, digest.digest());
            return bigInt.toString(16);
        } catch (Exception e) {
            log.error(e);
        }
        return null;
    }

    /**
     * 创建xml节点
     *
     * @return xml节点
     */
    private static DOMElement createDependency() {
        return new DOMElement("dependency");
    }

}
