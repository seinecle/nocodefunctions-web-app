///*
// * To change this license header, choose License Headers in Project Properties.
// * To change this template file, choose Tools | Templates
// * and open the template in the editor.
// */
//package net.clementlevallois.nocodeapp.web.front.backingbeans;
//
//import com.google.api.client.auth.oauth2.Credential;
//import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
//import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
//import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
//import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
//import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
//import com.google.api.client.http.FileContent;
//import com.google.api.client.http.javanet.NetHttpTransport;
//import com.google.api.client.json.JsonFactory;
//import com.google.api.client.json.gson.GsonFactory;
//import com.google.api.client.util.store.FileDataStoreFactory;
//import com.google.api.services.drive.Drive;
//import com.google.api.services.drive.DriveScopes;
//import com.google.api.services.drive.model.File;
//import com.google.api.services.drive.model.FileList;
//import com.google.api.services.drive.model.Permission;
//import com.google.api.services.slides.v1.Slides;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.InputStreamReader;
//import java.nio.file.Path;
//import java.security.GeneralSecurityException;
//import java.util.Collections;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//import java.util.logging.Level;
//import java.util.logging.Logger;
//import javax.annotation.PostConstruct;
//import javax.annotation.PreDestroy;
//import javax.ejb.Startup;
//import javax.enterprise.context.ApplicationScoped;
//import org.omnifaces.util.Faces;
//
///**
// *
// * @author LEVALLOIS
// */
//@Startup
//@ApplicationScoped
//public class SingletonGoogle {
//
//    public static final String TEMP_FOLDER_UMIGON_REPORTS = "1mpWY08eMqRrdOIL3He5AufNvRGisOb8T";
//    private static final String APPLICATION_NAME = "Report builder";
//    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
//    private static final String TOKENS_DIRECTORY_PATH = "tokens_gdrive";
//    private Drive service;
//    private Slides googleSlides;
//
//    /**
//     * Global instance of the scopes required by this quickstart. If modifying
//     * these scopes, delete your previously saved tokens/ folder.
//     */
//    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
//    private static final String CREDENTIALS_FILE_PATH = "credentials_gdrive.json";
//
//    /**
//     * Creates an authorized Credential object.
//     *
//     * @param HTTP_TRANSPORT The network HTTP Transport.
//     * @return An authorized Credential object.
//     * @throws IOException If the credentials.json file cannot be found.
//     */
//    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
//
//        // Load client secrets.
//        InputStream in = SingletonGoogle.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
//        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
//
//        // Build flow and trigger user authorization request.
//        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
//                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
//                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
//                .setAccessType("offline")
//                .build();
//        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8889).build();
//        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
//    }
//
//    @PostConstruct
//    private void initDriveService() {
//        try {
//
//            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
//            service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
//                    .setApplicationName(APPLICATION_NAME)
//                    .build();
//            googleSlides = new Slides.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
//                    .setApplicationName(APPLICATION_NAME)
//                    .build();
//        } catch (GeneralSecurityException | IOException ex) {
//            Logger.getLogger(SingletonGoogle.class.getName()).log(Level.SEVERE, null, ex);
//        }
//    }
//
//    public String createNewFolderForPresentation(String name, String idParentFolder) throws IOException {
//
//        // first checking if a folder with the name exists. If so, return its id
//        String pageToken = null;
//        String query = "parents in '" + idParentFolder + "'";
//        Set<File> prezFolders = new HashSet();
//        do {
//            FileList result = service.files().list()
//                    .setQ(query)
//                    .setPageToken(pageToken)
//                    .execute();
//            for (File file : result.getFiles()) {
//                prezFolders.add(file);
//            }
//            pageToken = result.getNextPageToken();
//        } while (pageToken != null);
//
//        for (File file : prezFolders) {
//            if (file.getName().equals(name)) {
//                return file.getId();
//            }
//        }
//
//        // if the folder with the name provided does not exist, create it.
//        File fileMetadata = new File();
//        fileMetadata.setName(name);
//        fileMetadata.setMimeType("application/vnd.google-apps.folder");
//        fileMetadata.setParents(Collections.singletonList(idParentFolder));
//        File file = service.files().create(fileMetadata).setFields("id, parents").execute();
//        return file.getId();
//    }
//
//    public String createNewSlidePresentationWithOnePage(String title, String parentFolderId) throws IOException {
//
//        // first checking if a presentation with the name exists. If so, return its id
//        String pageToken = null;
//        String query = "parents in '" + parentFolderId + "'";
//        Set<File> prezFolders = new HashSet();
//        do {
//            FileList result = service.files().list().setFields("*")
//                    .setQ(query)
//                    .setPageToken(pageToken)
//                    .execute();
//            for (File file : result.getFiles()) {
//                prezFolders.add(file);
//            }
//            pageToken = result.getNextPageToken();
//        } while (pageToken != null);
//
//        for (File file : prezFolders) {
//            if (file.getName().equals(title)) {
//                System.out.println("prez already exists");
//                if (file.getTrashed()) {
//                    System.out.println("the prez " + title + " exists but it is trashed. You must restaure it. Exiting.");
//                    return "";
//                }
//                return file.getId();
//            }
//        }
//
//        // if the presentation with the name provided does not exist, create it.
//        File fileMetadata = new File();
//        fileMetadata.setName(title);
//        fileMetadata.setMimeType("application/vnd.google-apps.presentation");
//        fileMetadata.setParents(Collections.singletonList(parentFolderId));
//        com.google.api.services.drive.model.File file = service.files().create(fileMetadata).setFields("id, parents").execute();
//        System.out.println("prez is created");
//
//        Permission domainPermission = new Permission()
//                .setType("anyone")
//                .setRole("reader");
//        service.permissions().create(file.getId(), domainPermission)
//                .setFields("id")
//                .execute();
//
//        return file.getId();
//
//    }
//
//    public String uploadPngPic(Path path, String fileName, String gDriveFolderId) throws IOException {
//
//        // first checking if a picture with the name exists. If so, delete it
//        String pageToken = null;
//        String query = "parents in '" + gDriveFolderId + "'";
//        Set<File> prezFolders = new HashSet();
//        do {
//            FileList result = service.files().list()
//                    .setQ(query)
//                    .setPageToken(pageToken)
//                    .execute();
//            for (File file : result.getFiles()) {
//                prezFolders.add(file);
//            }
//            pageToken = result.getNextPageToken();
//        } while (pageToken != null);
//
//        for (File file : prezFolders) {
//            if (file.getName().equals(fileName)) {
//                service.files().delete(file.getId()).execute();
//            }
//        }
//
//        // upload the picture
//        File fileMetadata = new File();
//        fileMetadata.setName(fileName)
//                .setParents(Collections.singletonList(gDriveFolderId));
//        java.io.File filePath = new java.io.File(path.toString() + java.io.File.separator + fileName);
//        FileContent mediaContent = new FileContent("image/png", filePath);
//        File file = service.files().create(fileMetadata, mediaContent)
//                .setFields("id, parents")
//                .execute();
//        System.out.println("png g drive id: " + file.getId());
//
//        Permission userPermission = new Permission()
//                .setType("anyone")
//                .setRole("writer");
//        service.permissions().create(file.getId(), userPermission)
//                .setFields("id")
//                .execute();
//
//        return file.getId();
//    }
//
//    public Slides getGoogleSlides() {
//        return googleSlides;
//    }
//
//    @PreDestroy
//    public void deleteSlidesReport() {
//        try {
//            service.files().delete(Faces.getSessionId()).execute();
//            System.out.println("prez is deleted");
//        } catch (IOException ex) {
//            Logger.getLogger(SingletonGoogle.class.getName()).log(Level.SEVERE, null, ex);
//        }
//    }
//
//}
