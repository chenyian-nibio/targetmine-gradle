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

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.util.FormattedTextParser;
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

    private static final String DATA_TYPE_DISEASE_OR_SYNDROME = "B2.2.1.2.1";

    private File mrStyFile;

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
        /**
         * Processing MRSTY.RRF file to collect UMLS's source
         */
        Iterator<String[]> mrStyIterator = getMrStyIterator();
        mrStyIterator.next(); // Skip header
        HashSet<String> cuiSet = new HashSet<>();
        while( mrStyIterator.hasNext() ) {

            String[] mrStyRow = mrStyIterator.next();
            String cui = mrStyRow[0];
            String str = mrStyRow[2];
            if(!str.startsWith(DATA_TYPE_DISEASE_OR_SYNDROME)) {
                continue;
            }
            cuiSet.add(cui);
        }

        try(BufferedReader reader1 = new BufferedReader(reader)){
            String line = null;
            HashSet<String> keySet = new HashSet<>();
            HashMap<String, Item> meshMap = new HashMap<>();
            HashMap<String, Item> umlsMap = new HashMap<>();
            while((line = reader1.readLine())!=null){
                String[] split = line.split("\\|");
                String identifer = split[0];
                if(!cuiSet.contains(identifer)) {
                    continue;
                }
                Item umlsDisease = umlsMap.get(identifer);
                if(umlsDisease==null){
                    umlsDisease = createItem("UMLSDisease");
                    umlsDisease.setAttribute("identifier",identifer);
                    String name = split[14];
                    umlsDisease.setAttribute("name",name);
                    store(umlsDisease);
                    umlsMap.put(identifer,umlsDisease);
                }

                String dbType = split[11];
                if("MSH".equals(dbType)){
                    String meshId = split[13];
                    String key = identifer+":"+meshId;
                    if(keySet.contains(key)) {
                        continue;
                    }
                    Item mesh = meshMap.get(meshId);
                    if(mesh==null){
                        mesh = createItem("MeSH");
                        mesh.setAttribute("identifier",meshId);
                        store(mesh);
                        meshMap.put(meshId,mesh);
                    }
                    Item meSHUMLS = createItem("MeSHUMLSDisease");
                    meSHUMLS.setReference("umls",umlsDisease);
                    meSHUMLS.setReference("mesh",mesh);
                    store(meSHUMLS);
                    keySet.add(key);
                }
            }
        }
    }

    private Iterator<String[]> getMrStyIterator() throws IOException {
        // delimiter '|'
        return FormattedTextParser.parseDelimitedReader( new FileReader( this.mrStyFile ), '|' );
    }

    public void setMrStyFile( File mrStyFile ) {
        this.mrStyFile = mrStyFile;
    }
}
