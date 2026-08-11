package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceArchivePackageService {

    private static final String MANIFEST_NAME = "manifest.json";
    private static final String CHECKSUMS_NAME = "checksums.sha256";

    private final EvidenceStorageService storageService;

    public EvidenceArchivePackageService(EvidenceStorageService storageService) {
        this.storageService = storageService;
    }

    public record ArchivePackageResult(
            String bucket,
            String packageObjectKey,
            String manifestObjectKey,
            int totalFiles,
            long totalBytes
    ) {
    }

    public ArchivePackageResult buildAndStore(
            EvidenceArchiveJob job,
            List<EvidenceManifestService.ArchivedEvidenceFile> files,
            byte[] manifestBytes,
            byte[] checksumsBytes) {
        try {
            String packageObjectKey = "archives/" + job.getJobCode()
                    + "/" + job.getJobCode() + ".zip";
            String manifestObjectKey = "archives/" + job.getJobCode()
                    + "/" + MANIFEST_NAME;
            byte[] zipBytes = buildZip(files, manifestBytes, checksumsBytes);
            String bucket = files.getFirst().bucket();

            storageService.putObject(
                    bucket,
                    packageObjectKey,
                    zipBytes,
                    "application/zip"
            );
            storageService.putObject(
                    bucket,
                    manifestObjectKey,
                    manifestBytes,
                    "application/json"
            );

            long totalBytes = files.stream()
                    .mapToLong(EvidenceManifestService.ArchivedEvidenceFile::sizeBytes)
                    .sum();

            return new ArchivePackageResult(
                    bucket,
                    packageObjectKey,
                    manifestObjectKey,
                    files.size(),
                    totalBytes
            );
        } catch (Exception exception) {
            throw new IllegalStateException("生成归档压缩包失败", exception);
        }
    }

    private byte[] buildZip(
            List<EvidenceManifestService.ArchivedEvidenceFile> files,
            byte[] manifestBytes,
            byte[] checksumsBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            writeEntry(zip, MANIFEST_NAME, manifestBytes);
            writeEntry(zip, CHECKSUMS_NAME, checksumsBytes);
            for (EvidenceManifestService.ArchivedEvidenceFile file : files) {
                zip.putNextEntry(new ZipEntry(file.archivePath()));
                try (InputStream stream = storageService.getObjectStream(
                        file.bucket(),
                        file.objectKey()
                )) {
                    stream.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static void writeEntry(
            ZipOutputStream zip,
            String name,
            byte[] content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }
}
