package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2019 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.BufferedReader;
import java.io.Reader;
import java.util.HashSet;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;


/**
 * 
 * @author
 */
public class UmlsConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "2018AB";
    private static final String DATA_SOURCE_NAME = "UMLS";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public UmlsConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        try(BufferedReader reader1 = new BufferedReader(reader)){
            String line = null;
            HashSet<String> idSet = new HashSet<>();
            while((line = reader1.readLine())!=null){
                String[] split = line.split("\\|");
                Item umlsDisease = createItem("UMLSDisease");
                String identifer = split[0];
                if(idSet.contains(identifer)) {
                    continue;
                }
                umlsDisease.setAttribute("identifier",identifer);
                String name = split[14];
                idSet.add(identifer);
                umlsDisease.setAttribute("name",name);
                store(umlsDisease);
            }
        }
    }
}
