package nl.knaw.dans.dataverse.bridge.easy;

import nl.knaw.dans.dataverse.bridge.IDataverseIngest;
import nl.knaw.dans.dataverse.bridge.util.DvnBridgeHelper;
import org.apache.abdera.i18n.iri.IRI;
import org.apache.abdera.model.Category;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.model.Link;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by akmi on 04/05/17.
 */
public class IngestToEasy implements IDataverseIngest {
    private String landingPage;

    private String doi;
    private static final Logger LOG = LoggerFactory.getLogger(IngestToEasy.class);

    @Override
    public String execute(File bagDir, IRI colIri, String uid, String pw) {
        StringBuffer sb = new StringBuffer("");
        // 0. Zip the bagDir
        File zipFile = new File(bagDir.getAbsolutePath() + ".zip");
        zipFile.delete();
        try {
            DvnBridgeHelper.zipDirectory(bagDir, zipFile);
            // 1. Set up stream for calculating MD5
            FileInputStream fis = new FileInputStream(zipFile);
            MessageDigest md = MessageDigest.getInstance("MD5");
            DigestInputStream dis = new DigestInputStream(fis, md);

            // 2. Post entire bag to Col-IRI
            CloseableHttpClient http = DvnBridgeHelper.createHttpClient(colIri.toURI(), uid, pw);
            CloseableHttpResponse response = DvnBridgeHelper.sendChunk(dis, (int) zipFile.length(), "POST", colIri.toURI(), "bag.zip", "application/zip", http, false);

            // 3. Check the response. If transfer corrupt (MD5 doesn't check out), report and exit.
            String bodyText = DvnBridgeHelper.readEntityAsString(response.getEntity());
            if (response.getStatusLine().getStatusCode() != 201) {
                LOG.error("FAILED. Status = " + response.getStatusLine());
                LOG.error("Response body follows:");
                LOG.error(bodyText);
            }
            LOG.info("SUCCESS. Deposit receipt follows:");
            sb.append("<bodyText>");
            sb.append(bodyText);
            sb.append("</bodyText>");
            LOG.info(bodyText);

            // 4. Get the statement URL. This is the URL from which to retrieve the current status of the deposit.
            LOG.info("Retrieving Statement IRI (Stat-IRI) from deposit receipt ...");
            Entry receipt = DvnBridgeHelper.parse(bodyText);
            Link statLink = receipt.getLink("http://purl.org/net/sword/terms/statement");
            IRI statIri = statLink.getHref();
            LOG.info("Stat-IRI = " + statIri);
            sb.append(trackDeposit(http, statIri.toURI()));
            // 5. Check statement every ten seconds (a bit too frantic, but okay for this test). If status changes:
            // report new status. If status is an error (INVALID, REJECTED, FAILED) or ARCHIVED: exit.
        } catch (Exception e) {
            LOG.error("ERROR: " + e.getMessage());
        }


        return sb.toString();
    }

    private String trackDeposit(CloseableHttpClient http, URI statUri) throws Exception {
        CloseableHttpResponse response;
        String bodyText;
        LOG.info("Start polling Stat-IRI for the current status of the deposit, waiting 10 seconds before every request ...");
        while (true) {
            Thread.sleep(1000);
            LOG.info("Checking deposit status ... ");
            response = http.execute(new HttpGet(statUri));
            bodyText = DvnBridgeHelper.readEntityAsString(response.getEntity());
            Feed statement = DvnBridgeHelper.parse(bodyText);
            List<Category> states = statement.getCategories("http://purl.org/net/sword/terms/state");
            if (states.isEmpty()) {
                bodyText = "ERROR: NO STATE FOUND";
                LOG.error(bodyText);
                return bodyText;
            } else if (states.size() > 1) {
                bodyText = "ERROR: FOUND TOO MANY STATES (" + states.size() + "). CAN ONLY HANDLE ONE";
                LOG.error(bodyText);
                return (bodyText);
            } else {
                String state = states.get(0).getTerm();
                LOG.info(state);
                String doiNumber = "";
                if (state.equals("INVALID") || state.equals("REJECTED") || state.equals("FAILED")) {
                    LOG.error("FAILURE. Complete statement follows:");
                    LOG.error(bodyText);
                    return (state);
                } else if (state.equals("ARCHIVED")) {
                    List<Entry> entries = statement.getEntries();
                    LOG.info("SUCCESS. ");
                    if (entries.size() == 1) {
                        LOG.info("Deposit has been archived at: [" + entries.get(0).getId() + "]. ");

                        List<String> dois = getDois(entries.get(0));
                        int numDois = dois.size();
                        switch (numDois) {
                            case 1:
                                LOG.info(" With DOI: [" + dois.get(0) + "]. ");
                                setDoi(dois.get(0));
                                break;
                            case 0:
                                LOG.info("WARNING: No DOI found");
                                break;

                            default:
                                System.out.println("WARNING: More than one DOI found (" + numDois + "): ");
                                for (String doi : dois) {
                                    LOG.info(" [" + doi + "]");
                                }

                                break;
                        }
                        if (entries.size() == 1) {
                            LOG.info("Deposit has been archived at: <" + entries.get(0).getId() + ">. ");
                        }
                        String stateText = states.get(0).getText();
                        if (stateText != null && !stateText.isEmpty())
                            stateText = stateText.replace("ui/datasets/easy", "ui/datasets/id/easy");

                        LOG.info("DvnBridgeDataset landing page will be located at: " + stateText);
                        LOG.info("Complete statement follows:");
                        LOG.info(bodyText);

                        setLandingPage(stateText);
                        return "<easyLandingPage>" + stateText + "</easyLandingPage>";
                    }
                }
            }
        }
    }

    @Override
    public String getLandingPage() {
        return landingPage;
    }

    @Override
    public String getDoi() {
        return doi;
    }

    private void setLandingPage(String landingPage) {
        this.landingPage = landingPage;
    }

    private void setDoi(String doi) {
        this.doi = doi;
    }

    private List<String> getDois(Entry entry) {
        List<String> dois = new ArrayList<String>();

        List<Link> links = entry.getLinks("self");
        for (Link link : links) {
            IRI href = link.getHref();
            if (href.getHost().equals("doi.org")) {
                String path = href.getPath();
                String doi = path.substring(1); // skip leading '/'
                dois.add(doi);
            }
        }
        return dois;
    }


}
