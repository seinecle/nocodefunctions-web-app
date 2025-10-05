/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.backingbeans;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    private Properties privateProperties;
    private Properties limitsProperties;
    private static Path rootProjectPath;
    private static Path i18nStaticResourcesFullPath;
    private Path userGeneratedVosviewerPublicDirectoryFullPath;
    private Path userGeneratedVosviewerPrivateDirectoryFullPath;
    private Path userGeneratedGephistoPublicDirectoryFullPath;
    private Path userGeneratedGephistoPrivateDirectoryFullPath;
    private Path userGeneratedGephiLitePublicDirectoryFullPath;
    private Path userGeneratedGephiLitePrivateDirectoryFullPath;
    private Path gephistoRootRelativePath;
    private Path gephiLiteRootRelativePath;
    private Path vosviewerRootRelativePath;
    private Path gephistoRootFullPath;
    private Path gephiLiteRootFullPath;
    private Path vosviewerRootFullPath;
    private Path tempFolderFullPath;

    private final String ENV_VARIABLE_ROOTPROJECT = "root.project";
    private final String ENV_VARIABLE_PROPERTIES_FILE = "properties.relative.path.and.filename";
    private final String ENV_VARIABLE_LIMITS_FILE = "limits.relative.path.and.filename";
    private static final String ENV_VARIABLE_I18N_DIR = "i18n.relative.path";
    private final String ENV_VARIABLE_VOSVIEWER_DIR = "relative.path.vosviewer";
    private final String ENV_VARIABLE_GEPHISTO_DIR = "relative.path.gephisto";
    private final String ENV_VARIABLE_GEPHILITE_DIR = "relative.path.gephilite";
    private final String ENV_VARIABLE_TEMP_DIR = "relative.path.temp";
    private final String ENV_VARIABLE_PUBLIC_DIR = "relative.path.public";
    private final String ENV_VARIABLE_PRIVATE_DIR = "relative.path.private";
    private final String ENV_VARIABLE_USER_CREATED_FILES_DIR = "relative.path.user.created.files";

    public ApplicationPropertiesBean() {
    }

    @PostConstruct
    public void loadAll() {
        loadEnvironmentVariables();
        rootProjectPath = loadRootProjectPath();
        privateProperties = loadPrivateProperties();
        limitsProperties = loadLimitsProperties();
        i18nStaticResourcesFullPath = loadI18nStaticResourcesFullPath();
        userGeneratedVosviewerPublicDirectoryFullPath = loadVosviewerPublicFullPath();
        userGeneratedVosviewerPrivateDirectoryFullPath = loadVosviewerPrivatePath();
        userGeneratedGephistoPublicDirectoryFullPath = loadGephistoPublicFullPath();
        userGeneratedGephistoPrivateDirectoryFullPath = loadGephistoPrivateFullPath();
        gephistoRootRelativePath = loadGephistoRootRelativePath();
        userGeneratedGephiLitePublicDirectoryFullPath = loadGephiLitePublicFullPath();
        userGeneratedGephiLitePrivateDirectoryFullPath = loadGephiLitePrivateFullPath();
        gephiLiteRootRelativePath = loadGephiLiteRootRelativePath();
        tempFolderFullPath = loadTempFolderFullPath();
        vosviewerRootRelativePath = loadVosviewerRootRelativePath();
        vosviewerRootFullPath = loadVosviewerRootFullPath();
        gephistoRootFullPath = loadGephistoRootFullPath();
        gephiLiteRootFullPath = loadGephiLiteRootFullPath();
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

    private Properties loadLimitsProperties() {
        Path limitsPropsFilePath = null;
        Properties props = null;
        String privateLimitsFilePathAsString = System.getProperty(ENV_VARIABLE_LIMITS_FILE);
        if (privateLimitsFilePathAsString == null || privateLimitsFilePathAsString.isBlank()) {
            System.out.println("system property for limits file relative path not correctly loaded");
            System.out.println("you need to add --systemproperties sys.properties in the command launching the app");
            System.out.println("where sys.properties is a text file in the same directory as the Payara server or any server you use");
            System.out.println("EXITING NOW because without these properties, the app can't function");
            System.exit(-1);
        } else {
            limitsPropsFilePath = rootProjectPath.resolve(Path.of(privateLimitsFilePathAsString));
            if (!Files.exists(limitsPropsFilePath)) {
                System.out.println("private limits file path loaded from env variables does not exist");
                System.out.println("path is: " + limitsPropsFilePath.toString());
                System.out.println("EXITING NOW because without these properties, the app can't function");
                System.exit(-1);
            }
        }
        try (InputStream is = new FileInputStream(limitsPropsFilePath.toFile())) {
            props = new Properties();
            props.load(is);

        } catch (IOException ex) {
            System.out.println("ex: " + ex);
            System.out.println("could not open the file for limits properties");
            System.out.println("path is: " + limitsPropsFilePath.toString());
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

    public Properties getLimitsProperties() {
        return limitsProperties;
    }

    public static Path getExternalFolderForInternationalizationFiles() {
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

    private Path loadGephiLitePublicFullPath() {
        String ug = System.getProperty(ENV_VARIABLE_USER_CREATED_FILES_DIR);
        String gephiLite = System.getProperty(ENV_VARIABLE_GEPHILITE_DIR);
        String publicFolder = System.getProperty(ENV_VARIABLE_PUBLIC_DIR);
        return rootProjectPath.resolve(Path.of(ug)).resolve(Path.of(gephiLite)).resolve(Path.of(publicFolder));
    }

    private Path loadGephiLitePrivateFullPath() {
        String ug = System.getProperty(ENV_VARIABLE_USER_CREATED_FILES_DIR);
        String gephiLite = System.getProperty(ENV_VARIABLE_GEPHILITE_DIR);
        String privateFolder = System.getProperty(ENV_VARIABLE_PRIVATE_DIR);
        return rootProjectPath.resolve(Path.of(ug)).resolve(Path.of(gephiLite)).resolve(Path.of(privateFolder));
    }

    private Path loadGephiLiteRootRelativePath() {
        String gephiLite = System.getProperty(ENV_VARIABLE_GEPHILITE_DIR);
        return Path.of(gephiLite);
    }

    private Path loadTempFolderFullPath() {
        String temp = System.getProperty(ENV_VARIABLE_TEMP_DIR);
        return Path.of(rootProjectPath.toString(), temp);
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

    private Path loadGephiLiteRootFullPath() {
        String ug = System.getProperty(ENV_VARIABLE_USER_CREATED_FILES_DIR);
        String gephiLite = System.getProperty(ENV_VARIABLE_GEPHILITE_DIR);
        return rootProjectPath.resolve(Path.of(ug)).resolve(Path.of(gephiLite));
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

    public Path getUserGeneratedGephiLitePublicDirectoryFullPath() {
        return userGeneratedGephiLitePublicDirectoryFullPath;
    }

    public Path getUserGeneratedGephiLitePrivateDirectoryFullPath() {
        return userGeneratedGephiLitePrivateDirectoryFullPath;
    }

    public Path getUserGeneratedGephiLiteDirectoryFullPath(boolean sharePublicly) {
        if (sharePublicly) {
            return userGeneratedGephiLitePublicDirectoryFullPath;
        } else {
            return userGeneratedGephiLitePrivateDirectoryFullPath;
        }
    }

    public Path getRelativePathFromProjectRootToGephistoFolder() {
        return getRootProjectFullPath().relativize(getGephistoRootFullPath());
    }

    public Path getRelativePathFromProjectRootToGephiLiteFolder() {
        return getRootProjectFullPath().relativize(getGephiLiteRootFullPath());
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

    public Path getGephiLiteRootRelativePath() {
        return gephiLiteRootRelativePath;
    }

    public Path getVosviewerRootRelativePath() {
        return vosviewerRootRelativePath;
    }

    public Path getGephistoRootFullPath() {
        return gephistoRootFullPath;
    }

    public Path getGephiLiteRootFullPath() {
        return gephiLiteRootFullPath;
    }

    public Path getVosviewerRootFullPath() {
        return vosviewerRootFullPath;
    }

    public Path getTempFolderFullPath() {
        return tempFolderFullPath;
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

    private void loadEnvironmentVariables() {
        if (RemoteLocal.isLocal()) {
            loadEnvironmentVariablesOnWindows();
        } else {
            loadEnvironmentVariablesOnLinux();
        }
    }

    private void loadEnvironmentVariablesOnLinux() {
        String currentWorkingDirectory = System.getProperty("user.dir");
        System.out.println("working dir: " + currentWorkingDirectory);
        Path pathToSystemProperties = Path.of("sys.properties");
        if (!Files.exists(Path.of("sys.properties"))) {
            pathToSystemProperties = Path.of("..", "sys.properties");
        }

        try {
            List<String> vars = Files.readAllLines(pathToSystemProperties, StandardCharsets.UTF_8);
            for (String line : vars) {
                if (line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("=", 2);
                System.setProperty(fields[0], fields[1]);
            }
        } catch (IOException ex) {
            System.out.println("running on linux, could not find the file sys.properties containing all environment variablesl");
            System.out.println("EXITING NOW because without these properties, the app can't function");
            System.out.println("ex: " + ex);
        }
    }

    private void loadEnvironmentVariablesOnWindows() {
        String currentWorkingDirectory = System.getProperty("user.dir");
        System.out.println("working dir: " + currentWorkingDirectory);
        try {
            List<String> vars = Files.readAllLines(Path.of("sys.properties"), StandardCharsets.UTF_8);
            for (String line : vars) {
                if (line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("=", 2);
                System.setProperty(fields[0], fields[1]);
            }
        } catch (IOException ex) {
            System.out.println("running on windows, could not find the file sys.properties containing all environment variables");
            System.out.println("EXITING NOW because without these properties, the app can't function");
            System.out.println("ex: " + ex);
        }
    }
}
