package nl.knaw.dans.dataverse.bridge.bagit;

import gov.loc.repository.bagit.Bag;
import gov.loc.repository.bagit.BagFactory;
import gov.loc.repository.bagit.BagInfoTxt;
import gov.loc.repository.bagit.transformer.Completer;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

/**
 * Class BagInfoCompleter
 * Created by Eko Indarto on 08/05/17.
 */
public class BagInfoCompleter implements Completer {
    BagFactory bagFactory;

    public BagInfoCompleter(BagFactory bagFactory) {
        this.bagFactory = bagFactory;
    }

    @Override
    public Bag complete(Bag bag) {
        Bag newBag = bagFactory.createBag(bag);

        // copy files from bag to newBag
        newBag.putBagFiles(bag.getPayload());
        newBag.putBagFiles(bag.getTags());
        // create a BagInfoTxt based on the old one
        Bag.BagPartFactory bagPartFactory = bagFactory.getBagPartFactory();
        BagInfoTxt bagInfo = bagPartFactory.createBagInfoTxt(bag.getBagInfoTxt());

        // add the CREATED field
        DateTime dt = new DateTime();

        bagInfo.put("Created", ISODateTimeFormat.dateTime().print(dt));


        // add the new BagInfoTxt to the newBag
        newBag.putBagFile(bagInfo);
        return newBag;
    }
}
