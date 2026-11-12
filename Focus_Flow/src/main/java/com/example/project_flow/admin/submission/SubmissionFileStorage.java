package com.example.project_flow.admin.submission;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * Keeps uploaded bytes outside the project and outside the database, by default in
 * %USERPROFILE%\focus_flow_uploads. Files are saved under random names, so a user-supplied name
 * can never choose where a file lands.
 */
@Component
public class SubmissionFileStorage {

    private final Path root;

    public SubmissionFileStorage(@Value("${focusflow.upload-dir:${user.home}/focus_flow_uploads}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    /** Writes the upload and returns the generated stored name. */
    public String store(Long submissionId, MultipartFile file) {
        String storedName = UUID.randomUUID().toString().replace("-", "") + extension(file.getOriginalFilename());
        Path target = folder(submissionId).resolve(storedName);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + storedName, e);
        }
        return storedName;
    }

    public Path path(Long submissionId, String storedName) {
        Path path = folder(submissionId).resolve(storedName).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid stored file name");
        }
        return path;
    }

    /** Best effort: a file that is already gone is not an error. */
    public void delete(Long submissionId, String storedName) {
        try {
            Files.deleteIfExists(path(submissionId, storedName));
        } catch (IOException ignored) {
            // The row is removed regardless; an orphaned file is harmless.
        }
    }

    public void deleteFolderIfEmpty(Long submissionId) {
        try {
            Files.deleteIfExists(folder(submissionId));
        } catch (IOException ignored) {
            // Not empty or still in use: leave it.
        }
    }

    private Path folder(Long submissionId) {
        return root.resolve("submissions").resolve(String.valueOf(submissionId));
    }

    private static String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,10}") ? "." + ext : "";
    }
}
