package nl.knaw.dans.dataverse.bridge.easy;

import nl.knaw.dans.dataverse.bridge.converter.DvnFile;
import org.joda.time.DateTime;

import java.util.List;

/**
 * Created by Eko Indarto on 08/05/17.
 */
public class DvnBridgeDataset {
    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    private String pid;
    private String identifier;
    private DateTime depositDate;
    private String fileLocationDir;
    private int version;
    private List<DvnFile> files;
    private List<EasyFileAttribute> easyFileAttribute;


    public DvnBridgeDataset(String pid) {
        setPid(pid);
        identifier = pid.split("/")[1];
        setFileLocationDir(pid.replace("hdl:", ""));
    }

    public String getIdentifier() {
        return identifier;
    }

    private void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public DateTime getDepositDate() {
        return depositDate;
    }

    public void setDepositDate(DateTime depositDate) {
        this.depositDate = depositDate;
    }

    public List<DvnFile> getFiles() {
        return files;
    }

    public void setFiles(List<DvnFile> files) {
        this.files = files;
    }

    public String getFileLocationDir() {
        return fileLocationDir;
    }

    private void setFileLocationDir(String fileLocationDir) {
        this.fileLocationDir = fileLocationDir;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<EasyFileAttribute> getEasyFileAttribute() {
        return easyFileAttribute;
    }

    public void setEasyFileAttribute(List<EasyFileAttribute> easyFileAttribute) {
        this.easyFileAttribute = easyFileAttribute;
    }


}
