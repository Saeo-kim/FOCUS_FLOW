package com.example.project_flow.admin.web;

import com.example.project_flow.admin.submission.SubmissionDtos.DashboardSummary;
import com.example.project_flow.admin.submission.SubmissionDtos.EditRequest;
import com.example.project_flow.admin.submission.SubmissionDtos.ReviewRequest;
import com.example.project_flow.admin.submission.SubmissionDtos.SubmissionDetail;
import com.example.project_flow.admin.submission.SubmissionDtos.SubmissionResponse;
import com.example.project_flow.admin.submission.SubmissionService;
import com.example.project_flow.admin.submission.SubmissionStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class SubmissionController {

    private final SubmissionService service;

    public SubmissionController(SubmissionService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public DashboardSummary dashboard() {
        return service.dashboard();
    }

    @GetMapping("/submissions")
    public List<SubmissionResponse> list(@RequestParam(required = false) Long projectId,
                                         @RequestParam(required = false) Long phaseId,
                                         @RequestParam(required = false) SubmissionStatus status,
                                         @RequestParam(required = false) String q) {
        return service.list(projectId, phaseId, status, q);
    }

    @GetMapping("/submissions/{id}")
    public SubmissionDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping(value = "/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionResponse create(@RequestParam(required = false) Long projectId,
                                     @RequestParam(required = false) Long phaseId,
                                     @RequestParam(required = false) Long taskId,
                                     @RequestParam(required = false) String title,
                                     @RequestParam(required = false) String note,
                                     @RequestParam(required = false) String submitterName,
                                     @RequestPart(name = "files", required = false) List<MultipartFile> files,
                                     HttpServletRequest request) {
        return service.create(UserSession.user(request), projectId, phaseId, taskId, title, note, submitterName, files);
    }

    @PostMapping(value = "/submissions/{id}/resubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SubmissionResponse resubmit(@PathVariable Long id,
                                       @RequestParam(required = false) String note,
                                       @RequestPart(name = "files", required = false) List<MultipartFile> files,
                                       HttpServletRequest request) {
        return service.resubmit(UserSession.user(request), id, note, files);
    }

    @PostMapping("/submissions/{id}/approve")
    public SubmissionResponse approve(@PathVariable Long id, @Valid @RequestBody(required = false) ReviewRequest body,
                                      HttpServletRequest request) {
        return service.review(UserSession.user(request), id, SubmissionStatus.APPROVED, body == null ? null : body.comment());
    }

    @PostMapping("/submissions/{id}/reject")
    public SubmissionResponse reject(@PathVariable Long id, @Valid @RequestBody(required = false) ReviewRequest body,
                                     HttpServletRequest request) {
        return service.review(UserSession.user(request), id, SubmissionStatus.REJECTED, body == null ? null : body.comment());
    }

    @PostMapping("/submissions/{id}/reopen")
    public SubmissionResponse reopen(@PathVariable Long id, HttpServletRequest request) {
        return service.reopen(UserSession.user(request), id);
    }

    @PutMapping("/submissions/{id}")
    public SubmissionResponse edit(@PathVariable Long id, @Valid @RequestBody EditRequest body, HttpServletRequest request) {
        return service.edit(UserSession.user(request), id, body);
    }

    @DeleteMapping("/submissions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    /** Always sent as a download, never rendered, so an uploaded HTML or SVG file cannot run in this site. */
    @GetMapping("/submission-files/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable Long fileId) {
        return asDownload(service.download(fileId));
    }

    static ResponseEntity<Resource> asDownload(SubmissionService.Download d) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(d.file().getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.file().getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(d.path()));
    }
}
