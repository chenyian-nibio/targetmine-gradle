package org.intermine.bio.dataconversion;

import org.intermine.dataconversion.ItemsTestCase;
import org.intermine.dataconversion.MockItemWriter;
import org.intermine.metadata.Model;

public class PossumConverterTest extends ItemsTestCase
{
    Model model = Model.getInstanceByName("genomic");
    PossumConverter converter;
    MockItemWriter itemWriter;

    public PossumConverterTest(String arg) {
        super(arg);
    }

    public void setUp() throws Exception {
        super.setUp();
        // if you need to do some preprocessing and set up, do that here
    }

    public void testProcess() throws Exception {
        // read in a test file (place in test/resources so it's on the classpath)
        // process using your converter
        // compare results to expected results
        // look at the other intermine unit tests for help!
    }
}
