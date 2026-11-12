package com.example.project_flow.admin.submission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One uploaded file. The bytes live on disk under the upload folder; this row is the index. */
@Entity
@Table(name = "submission_files")
public class SubmissionFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    /** The submission version this file was handed in with: 1 for the first upload, 2 after a resubmit, ... */
    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 255)
    private String originalName;

    @Column(nullable = false, unique = true, length = 80)
    private String storedName;

    @Column(length = 150)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    protected SubmissionFile() {
    }

    public SubmissionFile(Submission submission, int version, String originalName, String storedName,
                          String contentType, long sizeBytes, LocalDateTime uploadedAt) {
        this.submission = submission;
        this.version = version;
        this.originalName = originalName;
        this.storedName = storedName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() { return id; }
    public Submission getSubmission() { return submission; }
    public int getVersion() { return version; }
    public String getOriginalName() { return originalName; }
    public String getStoredName() { return storedName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
