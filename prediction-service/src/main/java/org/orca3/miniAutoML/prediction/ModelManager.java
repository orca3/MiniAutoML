package org.orca3.miniAutoML.prediction;

import com.google.common.collect.Maps;
import io.minio.DownloadObjectArgs;
import io.minio.MinioClient;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.orca3.miniAutoML.metadataStore.GetArtifactResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModelManager {
    private final String modelCachePath;
    private final MinioClient minioClient;
    private final Map<String, String> algorithmCache;

    public ModelManager(String modelCachePath, MinioClient minioClient) {
        this.modelCachePath = modelCachePath;
        this.minioClient = minioClient;
        this.algorithmCache = Maps.newHashMap();
    }

    public boolean contains(String runId) {
        return algorithmCache.containsKey(runId);
    }

    public void set(String runId, GetArtifactResponse artifactResponse) {
        try {
            File tarFile = new File(modelCachePath, String.format("%s.zip", runId));
            File untarDir = new File(modelCachePath, runId);
            untarDir.mkdir();
            minioClient.downloadObject(DownloadObjectArgs.builder()
                    .bucket(artifactResponse.getArtifact().getBucket())
                    .object(artifactResponse.getArtifact().getPath())
                    .filename(tarFile.getAbsolutePath())
                    .build());
            unTar(tarFile, untarDir);
            algorithmCache.put(runId, artifactResponse.getAlgorithm());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getAlgorithm(String runId) {
        return algorithmCache.get(runId);
    }

    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    private static void unTar(final File inputFile, final File outputDir) throws FileNotFoundException, IOException, ArchiveException {
        ZipInputStream zis = new ZipInputStream(new FileInputStream(inputFile));
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            File newFile = newFile(outputDir, zipEntry);
            if (zipEntry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {
                // fix for Windows-created archives
                File parent = newFile.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent);
                }

                // write file content
                FileOutputStream fos = new FileOutputStream(newFile);
                IOUtils.copy(zis, fos);
            }
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();
    }
}
