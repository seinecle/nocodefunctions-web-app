/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.backingbeans;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;

/**
 *
 * @author LEVALLOIS
 */
@Startup
@Singleton
public class ApplicationPropertiesBean {

    private final Properties privateProperties;
    private final Path rootProjectPath;
    private final Path i18nStaticResourcesFullPath;
    private final Path userGeneratedVosviewerPublicDirectoryFullPath;
    private final Path userGeneratedVosviewerPrivateDirectoryFullPath;
    private final Path userGeneratedGephistoPublicDirectoryFullPath;
    private final Path userGeneratedGephistoPrivateDirectoryFullPath;
    private final Path gephistoRootRelativePath;
    private final Path vosviewerRootRelativePath;
    private final Path gephistoRootFullPath;
    private final Path vosviewerRootFullPath;

    private final String ENV_VARIABLE_ROOTPROJECT = "root.project";
    private final String ENV_VARIABLE_PROPERTIES_FILE = "properties.relative.path.and.filename";
    private final String ENV_VARIABLE_I18N_DIR = "i18n.relative.path";
    private final String ENV_VARIABLE_VOSVIEWER_DIR = "relative.path.vosviewer";
    private final String ENV_VARIABLE_GEPHISTO_DIR = "relative.path.gephisto";
    private final String ENV_VARIABLE_PUBLIC_DIR = "relative.path.public";
    private final String ENV_VARIABLE_PRIVATE_DIR = "relative.path.private";
    private final String ENV_VARIABLE_USER_CREATED_FILES_DIR = "relative.path.user.created.files";

    public ApplicationPropertiesBean() {
        loadEnvironmentVariablesOnWindows();
        rootProjectPath = loadRootProjectPath();
        privateProperties = loadPrivateProperties();
        i18nStaticResourcesFullPath = loadI18nStaticResourcesFullPath();
        userGeneratedVosviewerPublicDirectoryFullPath = loadVosviewerPublicFullPath();
        userGeneratedVosviewerPrivateDirectoryFullPath = loadVosviewerPrivatePath();
        userGeneratedGephistoPublicDirectoryFullPath = loadGephistoPublicFullPath();
        userGeneratedGephistoPrivateDirectoryFullPath = loadGephistoPrivateFullPath();
        gephistoRootRelativePath = loadGephistoRootRelativePath();
        vosviewerRootRelativePath = loadVosviewerRootRelativePath();
        vosviewerRootFullPath = loadVosviewerRootFullPath();
        gephistoRootFullPath = loadGephistoRootFullPath();
    }

    private Path loadRootProjectPath() {
        String rootProjectProperty = System.getProperty(ENV_VARIABLE_ROOTPROJECT);
        Path rootPath = null;
        if (rootProjectProperty == null || rootProjectProperty.isBlank()) {
            System.out.println("system property for root project path not correctly loaded");
            System.out.println("you need to add --systemproperties sys.properties in the command launching the app");
            System.out.println("where sys.properties is a text file in the same directory as the Payara server or any server you use");
            System.out.println("EXITING NOW because without these properties, the app can't function");
            System.exit(-1);
        } else {
            rootPath = Path.of(rootProjectProperty);
            if (Files.isDirectory(rootPath)) {
                return rootPath;
            } else {
                System.out.println("root folder loaded from env variables does not exist");
                System.out.println("path is: " + rootProjectProperty);
                System.out.println("EXITING NOW because without these properties, the app can't function");
                System.exit(-1);
            }
        }
        return rootPath;
    }

    private Properties loadPrivateProperties() {
        Path privatePropsFilePath = null;
        Properties props = null;
        String privatePropsFilePathAsString = System.getProperty(ENV_VARIABLE_PROPERTIES_FILE);
        if (privatePropsFilePathAsString == null || privatePropsFilePathAsString.isBlank()) {
            System.out.println("system property for properties file relative path not correctly loaded");
            System.out.println("you need to add --systemproperties sys.properties in the command launching the app");
            System.out.println("where sys.properties is a text file in the same directory as the Payara server or any server you use");
            System.out.println("EXITING NOW because without these properties, the app can't function");
            System.exit(-1);
        } else {
            privatePropsFilePath = rootProjectPath.resolve(Path.of(privatePropsFilePathAsString));
            if (!Files.exists(privatePropsFilePath)) {
                System.out.println("private properties file path loaded from env variables does not exist");
                System.out.println("path is: " + privatePropsFilePath.toString());
                System.out.println("EXITING NOW because without these properties, the app can't function");
                System.exit(-1);
            }
        }
        try (InputStream is = new FileInputStream(privatePropsFilePath.toFile())) {
            props = new Properties();
            props.load(is);
        } catch (IOException ex) {
            System.out.println("ex: " + ex);
            System.out.println("could not open the file for private properties");
            System.out.println("path is: " + privatePropsFilePath.toString());
            System.out.println("EXITING NOW because without these properties, the app can't function");
            System.exit(-1);
        }
        return props;

    }

    private Path loadI18nStaticResourcesFullPath() {
        Path i18nResourcesFullPath = null;
        String i18nStaticResourcesRelativePath = System.getProperty(ENV_VARIABLE_I18N_DIR);
        if (i18nStaticResourcesRelativePath == null || i18nStaticResourcesRelativePath.isBlank()) {
            System.out.println("system property for i18n resources relative path not correctly loaded");
            System.out.println("you need to add --systemproperties sys.properties in the command launching the app");
            System.out.println("where sys.properties is a text file in the same directory as the Payara server or any server you use");
            System.out.println("EXITING NOW because without these properties, the app can't function");
            System.exit(-1);
        } else {
            i18nResourcesFullPath = rootProjectPath.resolve(Path.of(i18nStaticResourcesRelativePath));
            if (Files.isDirectory(i18nResourcesFullPath)) {
                return i18nResourcesFullPath;
            } else {
                System.out.println("directory for i18n resources loaded from env variables does not exist");
                System.out.println("path is: " + i18nResourcesFullPath.toString());
                System.out.println("EXITING NOW because without these properties, the app can't function");
                System.exit(-1);
            }
        }
        return i18nResourcesFullPath;
    }

    public Properties getPrivateProperties() {
        return privateProperties;
    }

    public String getHostFunctionsAPI() {
        URI uri;

        if (RemoteLocal.isLocal()) {
            uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withHost("localhost")
                    .withPort((Integer.valueOf(privateProperties.getProperty("nocode_api_port")))).toUri();
            return uri.toString();
        } else {
            UrlBuilder urlBuilder = UrlBuilder
                    .empty()
                    .withScheme("https");
            String domain;
            if (System.getProperty("test") != null && System.getProperty("test").equals("yes")) {
                domain = "test.nocodefunctions.com";
            } else {
                domain = "nocodefunctions.com";
            }
            urlBuilder.withHost(domain);
            return urlBuilder.toUrl().toString();
        }
    }

    public Path getExternalFolderForInternationalizationFiles() {
        return i18nStaticResourcesFullPath;
    }

    private Path loadVosviewerPrivatePath() {
        String ug = System.getProperty(ENV_VARIABLE_USER_CREATED_FILES_DIR);
        String vv = System.getProperty(ENV_VARIABLE_VOSVIEWER_DIR);
        String privateFolder = System.getProperty(ENV_VARIABLE_PRIVATE_DIR);
        return rootProjectPath.resolve(Path.of(ug)).resolve(Path.of(vv)).resolve(Path.of(privateFolder));
    }

    private Path loadVosviewerPublicFullPath() {
        String ug = System.getProperty(ENV_VARIABLE_USER_CREATED_FILES_DIR);
        String vv = System.getProperty(ENV_VARIABLE_VOSVIEWER_DIR);
        String publicFolder = System.getProperty(ENV_VARIABLE_PUBLIC_DIR);
        return rootProjectPath.resolve(Path.of(ug)).resolve(Path.of(vv)).resolve(Path.of(publicFolder));
    }

    private Path loadGephistoPublicFullPath() {
        String ug = System.getProperty(ENV_VARIABLE_USER_CREATED_FILES_DIR);
        String gephisto = System.getProperty(ENV_VARIABLE_GEPHISTO_DIR);
        String publicFolder = System.getProperty(ENV_VARIABLE_PUBLIC_DIR);
        return rootProjectPath.resolve(Path.of(ug)).resolve(Path.of(gephisto)).resolve(Path.of(publicFolder));
    }

    private Path loadGephistoPrivateFullPath() {
        String ug = System.getProperty(ENV_VARIABLE_USER_CREATED_FILES_DIR);
        String gephisto = System.getProperty(ENV_VARIABLE_GEPHISTO_DIR);
        String privateFolder = System.getProperty(ENV_VARIABLE_PRIVATE_DIR);
        return rootProjectPath.resolve(Path.of(ug)).resolve(Path.of(gephisto)).resolve(Path.of(privateFolder));
    }

    private Path loadGephistoRootRelativePath() {
        String gephisto = System.getProperty(ENV_VARIABLE_GEPHISTO_DIR);
        return Path.of(gephisto);
    }

    private Path loadVosviewerRootRelativePath() {
        String vosviewer = System.getProperty(ENV_VARIABLE_VOSVIEWER_DIR);
        return Path.of(vosviewer);
    }

    private Path loadGephistoRootFullPath() {
        String ug = System.getProperty(ENV_VARIABLE_USER_CREATED_FILES_DIR);
        String gephisto = System.getProperty(ENV_VARIABLE_GEPHISTO_DIR);
        return rootProjectPath.resolve(Path.of(ug)).resolve(Path.of(gephisto));
    }

    private Path loadVosviewerRootFullPath() {
        String ug = System.getProperty(ENV_VARIABLE_USER_CREATED_FILES_DIR);
        String vosviewer = System.getProperty(ENV_VARIABLE_VOSVIEWER_DIR);
        return rootProjectPath.resolve(Path.of(ug)).resolve(Path.of(vosviewer));
    }

    public Path getUserGeneratedVosviewerPublicDirectoryFullPath() {
        return userGeneratedVosviewerPublicDirectoryFullPath;
    }

    public Path getUserGeneratedVosviewerPrivateDirectoryFullPath() {
        return userGeneratedVosviewerPrivateDirectoryFullPath;
    }

    public Path getUserGeneratedVosviewerDirectoryFullPath(boolean sharePublicly) {
        if (sharePublicly) {
            return userGeneratedVosviewerPublicDirectoryFullPath;
        } else {
            return userGeneratedVosviewerPrivateDirectoryFullPath;
        }
    }

    public Path getRelativePathFromProjectRootToVosviewerFolder() {
        return getRootProjectFullPath().relativize(getVosviewerRootFullPath());
    }

    public Path getUserGeneratedGephistoPublicDirectoryFullPath() {
        return userGeneratedGephistoPublicDirectoryFullPath;
    }

    public Path getUserGeneratedGephistoPrivateDirectoryFullPath() {
        return userGeneratedGephistoPrivateDirectoryFullPath;
    }

    public Path getUserGeneratedGephistoDirectoryFullPath(boolean sharePublicly) {
        if (sharePublicly) {
            return userGeneratedGephistoPublicDirectoryFullPath;
        } else {
            return userGeneratedGephistoPrivateDirectoryFullPath;
        }
    }

    public Path getRelativePathFromProjectRootToGephistoFolder() {
        return getRootProjectFullPath().relativize(getGephistoRootFullPath());
    }

    public Path getRootProjectFullPath() {
        return rootProjectPath;
    }

    public Path getI18nStaticResourcesFullPath() {
        return i18nStaticResourcesFullPath;
    }

    public Path getGephistoRootRelativePath() {
        return gephistoRootRelativePath;
    }

    public Path getVosviewerRootRelativePath() {
        return vosviewerRootRelativePath;
    }

    public Path getGephistoRootFullPath() {
        return gephistoRootFullPath;
    }

    public Path getVosviewerRootFullPath() {
        return vosviewerRootFullPath;
    }

    public String getMiddlewarePort() {
        if (RemoteLocal.isLocal()) {
            return privateProperties.getProperty("middleware_local_port");
        } else {
            return privateProperties.getProperty("middleware_remote_port");
        }
    }

    public String getMiddlewareHost() {
        if (RemoteLocal.isLocal()) {
            return privateProperties.getProperty("middleware_local_host");
        } else {
            return privateProperties.getProperty("middleware_remote_host");
        }
    }

    private void loadEnvironmentVariablesOnWindows() {
        if (!RemoteLocal.isLocal()) {
            return;
        }
        List<String> vars = null;
        String currentWorkingDirectory = System.getProperty("user.dir");
        System.out.println("working dir: " + currentWorkingDirectory);
        try {
            vars = Files.readAllLines(Path.of("sys.properties"), StandardCharsets.UTF_8);
            for (String line : vars) {
                if (line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("=");
                System.setProperty(fields[0], fields[1]);
            }
        } catch (IOException ex) {
            System.out.println("running on windows, could not find the file sys.properties containing all environment variablesl");
            System.out.println("EXITING NOW because without these properties, the app can't function");
            System.out.println("ex: " + ex);
        }
    }
}
