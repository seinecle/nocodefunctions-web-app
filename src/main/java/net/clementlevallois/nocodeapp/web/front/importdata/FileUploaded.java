package net.clementlevallois.nocodeapp.web.front.importdata;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;
import net.clementlevallois.functions.model.Globals;

public record FileUploaded(byte[] bytes, String fileName, String fileUniqueId) {

    public FileUploaded(InputStream is, String fileName) throws IOException {
        this(is.readAllBytes(), Objects.requireNonNull(fileName), Globals.UPLOADED_FILE_PREFIX + UUID.randomUUID().toString().substring(0, 5));
    }
}