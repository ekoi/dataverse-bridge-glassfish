package nl.knaw.dans.dataverse.bridge.util;

import gov.loc.repository.bagit.BagFactory;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.abdera.Abdera;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Element;
import org.apache.abdera.parser.Parser;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URI;
import java.security.DigestInputStream;
import java.util.EnumSet;
import java.util.Optional;

/*
NOTES: The most part of the original code comes from
https://github.com/DANS-KNAW/easy-sword2-dans-examples/blob/master/src/main/java/nl/knaw/dans/easy/sword2examples/Common.java
 */
public class DvnBridgeHelper {
    static final String BAGIT_URI = "http://purl.org/net/sword/package/BagIt";
    static final BagFactory bagFactory = new BagFactory();

    public static void zipDirectory(File dir, File zipFile) throws Exception {
        if (zipFile.exists()) zipFile.delete();
        ZipFile zf = new ZipFile(zipFile);
        ZipParameters parameters = new ZipParameters();
        zf.addFolder(dir, parameters);
    }

    public static File copyToTarget(File dir) throws Exception {
        File dirInTarget = new File("target", dir.getName());
        FileUtils.deleteQuietly(dirInTarget);
        FileUtils.copyDirectory(dir, dirInTarget);
        return dirInTarget;
    }

    public static CloseableHttpClient createHttpClient(URI uri, String uid, String pw) {
        BasicCredentialsProvider credsProv = new BasicCredentialsProvider();
        credsProv.setCredentials(new AuthScope(uri.getHost(), uri.getPort()), new UsernamePasswordCredentials(uid, pw));
        return HttpClients.custom().setDefaultCredentialsProvider(credsProv).build();
    }

    public static CloseableHttpResponse sendChunk(DigestInputStream dis, int size, String method, URI uri, String filename, String mimeType, CloseableHttpClient http, boolean inProgress) throws Exception {
        byte[] chunk = readChunk(dis, size);
        String md5 = new String(Hex.encodeHex(dis.getMessageDigest().digest()));
        HttpUriRequest request = RequestBuilder.create(method).setUri(uri).setConfig(RequestConfig.custom()
        /*
         * When using an HTTPS-connection EXPECT-CONTINUE must be enabled, otherwise buffer overflow may follow
         */
                .setExpectContinueEnabled(true).build()) //
                .addHeader("Content-Disposition", String.format("attachment; filename=%s", filename)) //
                .addHeader("Content-MD5", md5) //
                .addHeader("Packaging", BAGIT_URI) //
                .addHeader("In-Progress", Boolean.toString(inProgress)) //
                .setEntity(new ByteArrayEntity(chunk, ContentType.create(mimeType))) //
                .build();
        CloseableHttpResponse response = http.execute(request);
        return response;
    }

    private static byte[] readChunk(InputStream is, int size) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] bytes = new byte[size];
        int c = is.read(bytes);
        bos.write(bytes, 0, c);
        return bos.toByteArray();
    }

    public static String readEntityAsString(HttpEntity entity) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IOUtils.copy(entity.getContent(), bos);
        return new String(bos.toByteArray(), "UTF-8");
    }

    public static <T extends Element> T parse(String text) {
        Abdera abdera = Abdera.getInstance();
        Parser parser = abdera.getParser();
        Document<T> receipt = parser.parse(new StringReader(text));
        return receipt.getRoot();
    }

    public static <T extends Enum<T>> Optional<T> valueOf(Class<T> clazz, String name) {
        return EnumSet.allOf(clazz).stream().filter(v -> v.name().equals(name))
                .findAny();
    }
}
