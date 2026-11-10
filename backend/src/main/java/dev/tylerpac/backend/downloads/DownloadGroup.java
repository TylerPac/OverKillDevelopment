package dev.tylerpac.backend.downloads;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// A file group's mod folder (for example "@OverKillDevCore"): loose files of its client and server parts are
// placed under it in the downloads.
@Entity
@Table(name = "download_groups")
public class DownloadGroup {

    @Id
    @Column(length = 64)
    private String baseKey;

    @Column(nullable = false, length = 100)
    private String modFolder;

    @Column(nullable = false)
    private Instant updatedAt;

    protected DownloadGroup() {
    }

    public DownloadGroup(String baseKey, String modFolder) {
        this.baseKey = baseKey;
        setModFolder(modFolder);
    }

    public void setModFolder(String modFolder) {
        this.modFolder = modFolder;
        this.updatedAt = Instant.now();
    }

    public String getBaseKey() {
        return baseKey;
    }

    public String getModFolder() {
        return modFolder;
    }
}
