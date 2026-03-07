package com.gedavocat.util;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Implémentation de MultipartFile basée sur un tableau d'octets en mémoire.
 * Utilisée notamment pour passer un PDF filigrané à {@link com.gedavocat.service.DocumentService}
 * sans avoir besoin de réécrire le fichier temporaire sur disque.
 */
public class ByteArrayMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] content;

    public ByteArrayMultipartFile(String name, String originalFilename,
                                   String contentType, byte[] content) {
        this.name             = name;
        this.originalFilename = originalFilename;
        this.contentType      = contentType;
        this.content          = content;
    }

    @Override 
    @NonNull
    public String getName() { return name; }
    
    @Override 
    @Nullable
    public String getOriginalFilename() { return originalFilename; }
    
    @Override 
    @Nullable
    public String getContentType() { return contentType; }
    
    @Override 
    public boolean isEmpty() { return content == null || content.length == 0; }
    
    @Override 
    public long getSize() { return content == null ? 0 : content.length; }
    
    @Override 
    @NonNull
    public byte[] getBytes() { return content != null ? content : new byte[0]; }
    
    @Override 
    @NonNull
    public InputStream getInputStream() { 
        return new ByteArrayInputStream(content != null ? content : new byte[0]); 
    }

    @Override
    public void transferTo(@NonNull java.io.File dest) throws IOException {
        try (var out = new java.io.FileOutputStream(dest)) {
            if (content != null) {
                out.write(content);
            }
        }
    }
}
