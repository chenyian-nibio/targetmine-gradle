package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2018 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.Reader;
import java.util.Iterator;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;
import org.intermine.util.FormattedTextParser;

/**
 * 
 * @author chenyian
 */
public class ChromosomeConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "Gene";
    private static final String DATA_SOURCE_NAME = "NCBI";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public ChromosomeConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
        while (iterator.hasNext()) {
            String[] cols = iterator.next();
            String taxonId = cols[0];
            String chrId = cols[1];
            String identifier = cols[2];
            String length = cols[3];

            Item chromosome = createItem("Chromosome");
            chromosome.setReference("organism", getOrganism(taxonId));
            chromosome.setAttribute("primaryIdentifier", chrId);
            chromosome.setAttribute("secondaryIdentifier", identifier);
            chromosome.setAttribute("symbol", chrId);
            chromosome.setAttribute("length", length);
            store(chromosome);
        }
        reader.close();
    }

}
