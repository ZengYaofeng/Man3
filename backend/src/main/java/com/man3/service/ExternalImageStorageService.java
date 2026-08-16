package com.man3.service;

import com.man3.config.IkanmhProperties;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/** Downloads and verifies external images before the reader can serve them. */
@Service
public class ExternalImageStorageService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final int MAX_IMAGE_BYTES = 30 * 1024 * 1024;

    private final IkanmhProperties properties;

    public ExternalImageStorageService(IkanmhProperties properties) {
        this.properties = properties;
    }

    public StoredImage downloadYuyumhImage(long chapterId, int pageNo, String imageUrl, String chapterUrl,
                                            Map<String, String> cookies, String cacheKey) throws IOException {
        byte[] bytes = imageUrl.contains("/break_")
                ? downloadAndDecodeYuyumhMonga(imageUrl, chapterUrl, cookies, cacheKey)
                : download(imageUrl, chapterUrl, cookies);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IOException("Source response is not a readable image: " + imageUrl);
        }

        String extension = extension(imageUrl);
        String relativePath = "yuyumh/" + chapterId + "/" + String.format("%04d", pageNo) + "." + extension;
        Path destination = resolve(relativePath);
        Files.createDirectories(destination.getParent());
        Files.write(destination, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return new StoredImage(relativePath, bytes.length, image.getWidth(), image.getHeight());
    }

    /**
     * Comicbox's data-src is deliberately not an image URL. It identifies two encrypted .b_N chunks;
     * after AES-CBC decryption their first bytes contain image metadata and the vertical strips are reversed.
     */
    private byte[] downloadAndDecodeYuyumhMonga(String sourceUrl, String chapterUrl, Map<String, String> cookies,
                                                 String cacheKey) throws IOException {
        try {
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            for (int part = 0; part < 2; part++) {
                payload.write(decryptYuyumhChunk(download(yuyumhChunkUrl(sourceUrl, part, cacheKey), chapterUrl, cookies)));
            }
            byte[] decoded = payload.toByteArray();
            MongaMetadata metadata = restoreImageHeader(decoded);
            BufferedImage scrambled = ImageIO.read(new ByteArrayInputStream(decoded));
            if (scrambled == null) throw new IOException("Decoded yuyumh payload is not an image: " + sourceUrl);

            BufferedImage merged = "monga".equals(metadata.type)
                    ? mergeMonga(scrambled, metadata.bookId, metadata.pageNumber)
                    : scrambled;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(merged, "jpg", output)) throw new IOException("JPEG encoder is unavailable");
            return output.toByteArray();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Unable to decode yuyumh encrypted image: " + sourceUrl, e);
        }
    }

    private String yuyumhChunkUrl(String sourceUrl, int part, String cacheKey) throws Exception {
        URI source = new URI(sourceUrl);
        String path = source.getPath().replaceFirst("/break[^/]+/", "/")
                .replaceFirst("(?i)\\.(jpeg|jpg|png|gif|avif|webp)$", ".b_" + part);
        if (!path.contains(".b_" + part)) throw new IOException("Unsupported yuyumh image URL: " + sourceUrl);
        String root = source.getScheme() + "://" + source.getAuthority() + "/break_2";
        return root + path + "?v=" + URLEncoder.encode(cacheKey == null ? "" : cacheKey, "UTF-8");
    }

    private byte[] decryptYuyumhChunk(byte[] encrypted) throws Exception {
        byte[] key = "aaaaaaaaaaaaaaaa".getBytes("US-ASCII");
        byte[] iv = "0123456789aaaaaa".getBytes("US-ASCII");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(encrypted);
    }

    private MongaMetadata restoreImageHeader(byte[] bytes) throws IOException {
        if (bytes.length < 12) throw new IOException("Decoded yuyumh payload is too short");
        int format = bytes[0] & 0xff;
        int subtype = bytes[1] & 0xff;
        if (format != 0 || subtype != 0) {
            throw new IOException("Unsupported yuyumh image metadata: format=" + format + ", subtype=" + subtype);
        }
        int bookId = ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
        int pageNumber = ((bytes[4] & 0xff) << 24) | ((bytes[5] & 0xff) << 16)
                | ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
        byte[] jpegHeader = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 0x10,
                0x4a, 0x46, 0x49, 0x46, 0, 0x01};
        System.arraycopy(jpegHeader, 0, bytes, 0, jpegHeader.length);
        return new MongaMetadata("monga", bookId, pageNumber);
    }

    private BufferedImage mergeMonga(BufferedImage source, int bookId, int pageNumber) {
        int stripCount = mongaStripCount(bookId, pageNumber);
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            int remainder = height % stripCount;
            for (int index = 0; index < stripCount; index++) {
                int stripHeight = height / stripCount;
                int targetY = stripHeight * index;
                int sourceY = height - stripHeight * (index + 1) - remainder;
                if (index == 0) stripHeight += remainder;
                else targetY += remainder;
                graphics.drawImage(source, 0, targetY, width, targetY + stripHeight,
                        0, sourceY, width, sourceY + stripHeight, null);
            }
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private int mongaStripCount(int bookId, int pageNumber) {
        String digest = md5(bookId + String.valueOf(pageNumber));
        int digit = digest.charAt(digest.length() - 1) % 10;
        return 44 + digit * 4;
    }

    private String md5(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("MD5").digest(value.getBytes("UTF-8"));
            StringBuilder result = new StringBuilder(32);
            for (byte b : bytes) result.append(String.format("%02x", b & 0xff));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 is unavailable", e);
        }
    }

    private static class MongaMetadata {
        private final String type;
        private final int bookId;
        private final int pageNumber;

        private MongaMetadata(String type, int bookId, int pageNumber) {
            this.type = type;
            this.bookId = bookId;
            this.pageNumber = pageNumber;
        }
    }

    public Path resolve(String relativePath) throws IOException {
        if (relativePath == null || relativePath.trim().isEmpty()) throw new IOException("Image local path is empty");
        Path root = storageRoot();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) throw new IOException("Invalid image local path");
        return resolved;
    }

    public void deleteYuyumhChapter(long chapterId) throws IOException {
        Path directory = resolve("yuyumh/" + chapterId);
        if (!Files.exists(directory)) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new ImageStorageRuntimeException(e);
                }
            });
        } catch (ImageStorageRuntimeException e) {
            throw e.getCause();
        }
    }

    private byte[] download(String imageUrl, String chapterUrl, Map<String, String> cookies) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = openConnection(url);
        try {
            connection.setConnectTimeout(Math.max(10000, properties.getTimeoutMs()));
            connection.setReadTimeout(Math.max(10000, properties.getTimeoutMs()));
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Referer", chapterUrl);
            connection.setRequestProperty("Origin", "https://www.comicbox.xyz");
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            connection.setRequestProperty("Sec-Fetch-Dest", "image");
            connection.setRequestProperty("Sec-Fetch-Mode", "no-cors");
            connection.setRequestProperty("Sec-Fetch-Site", "cross-site");
            if (cookies != null && !cookies.isEmpty()) connection.setRequestProperty("Cookie", cookieHeader(cookies));

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Image request returned HTTP " + status + ": " + imageUrl);
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_IMAGE_BYTES) throw new IOException("Image exceeds size limit: " + imageUrl);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (output.size() + read > MAX_IMAGE_BYTES) throw new IOException("Image exceeds size limit: " + imageUrl);
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(URL url) throws IOException {
        if (!properties.isProxyEnabled()) return (HttpURLConnection) url.openConnection();
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(properties.getProxyHost(), properties.getProxyPort()));
        return (HttpURLConnection) url.openConnection(proxy);
    }

    private Path storageRoot() throws IOException {
        Path root = Paths.get(properties.getDownloadDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    private String extension(String imageUrl) {
        String path = imageUrl.split("[?]", 2)[0].toLowerCase();
        if (path.endsWith(".png")) return "png";
        if (path.endsWith(".gif")) return "gif";
        if (path.endsWith(".webp")) return "webp";
        return "jpg";
    }

    private String cookieHeader(Map<String, String> cookies) {
        StringBuilder value = new StringBuilder();
        for (Map.Entry<String, String> cookie : cookies.entrySet()) {
            if (value.length() > 0) value.append("; ");
            value.append(cookie.getKey()).append('=').append(cookie.getValue());
        }
        return value.toString();
    }

    public static class StoredImage {
        private final String localPath;
        private final long fileSize;
        private final int width;
        private final int height;

        public StoredImage(String localPath, long fileSize, int width, int height) {
            this.localPath = localPath;
            this.fileSize = fileSize;
            this.width = width;
            this.height = height;
        }

        public String getLocalPath() { return localPath; }
        public long getFileSize() { return fileSize; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }

    private static class ImageStorageRuntimeException extends RuntimeException {
        private ImageStorageRuntimeException(IOException cause) { super(cause); }
        @Override public synchronized IOException getCause() { return (IOException) super.getCause(); }
    }
}
