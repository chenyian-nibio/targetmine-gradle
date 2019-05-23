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

import java.io.BufferedReader;
import java.io.Reader;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;
import org.json.JSONObject;


/**
 * 
 * @author
 */
public class WhoTrialConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "who-trial";
    private static final String DATA_SOURCE_NAME = "who-trial";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public WhoTrialConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        try(BufferedReader br = new BufferedReader(reader)){
            String line = br.readLine();
            JSONObject jsonObject = new JSONObject(line);
            JSONObject main = jsonObject.getJSONObject("main");
            Item whoTrial = createItem("WhoTrial");
            whoTrial.setAttribute("name",main.getString("Main ID"));
            whoTrial.setAttribute("title",main.getString("Public title"));
            whoTrial.setAttribute("condition",jsonObject.getString("disease"));
            store(whoTrial);
        }
    }
}
