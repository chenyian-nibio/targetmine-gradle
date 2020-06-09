package org.intermine.bio.dataconversion;

import org.intermine.dataconversion.ItemsTestCase;
import org.intermine.dataconversion.MockItemWriter;
import org.intermine.metadata.Model;

/*
 * Copyright (C) 2002-2018 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

public class TrialtroveConverterTest extends ItemsTestCase
{
    Model model = Model.getInstanceByName("genomic");
    TrialtroveConverter converter;
    MockItemWriter itemWriter;

    public TrialtroveConverterTest(String arg) {
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
