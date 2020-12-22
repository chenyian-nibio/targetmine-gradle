package org.intermine.bio.web.displayer;

import java.util.ArrayList;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.intermine.api.InterMineAPI;
import org.intermine.model.InterMineObject;
import org.intermine.web.displayer.ReportDisplayer;
import org.intermine.web.logic.config.ReportDisplayerConfig;
import org.intermine.web.logic.results.ReportObject;

/**
 *
 */
public class BioActivityGraphDisplayer extends ReportDisplayer {

  /* log messages */
  /* log output printed to catalina.out */
  protected static final Logger LOG = Logger.getLogger(BioActivityGraphDisplayer.class);

  /**
   * constructor
   * Use super class to define required structures
   */
	public BioActivityGraphDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
		super(config, im);
	}

  /**
   *
   */
	@SuppressWarnings("unchecked")
	@Override
	public void display(HttpServletRequest request, ReportObject reportObject) {
    ArrayList<String> data = new ArrayList<String>();

		InterMineObject compound = (InterMineObject) reportObject.getObject();
    try{
      /* Get the compound's identifier */
      String identifier = (String) compound.getFieldValue("identifier");

      /* Fill the names of the columns for the data used for the visualization */
      data.add("Primary Accession\tGene Symbol\tOrganism Name\tActivity Type\tActivity Concentration");

      /* Retrieve the collection of Compound-Protein interactions, and iterate
       * over it to get the details of each one */
      Set<InterMineObject> compProtInt = (Set<InterMineObject>)compound.getFieldValue("targetProteins");
      for( InterMineObject interaction: compProtInt ){
        /* each interaction has an associated protein and a collection of
         * activities that we need to retrieve */
        InterMineObject protein = (InterMineObject) interaction.getFieldValue("protein");
        Set<InterMineObject> activities = (Set<InterMineObject>) interaction.getFieldValue("activities");

        /* In terms of the protein, we are interested in its primary accession,
         * DB identifier (later gene symbol) and organism (for its name) */
        Boolean isCanonical = (Boolean) protein.getFieldValue("isUniprotCanonical");

        if( isCanonical!=null && isCanonical.booleanValue() ){
          String primaryAccession = (String) protein.getFieldValue("primaryAccession");
          /* changed to symbol
           * String dbIdentifier = (String) protein.getFieldValue("primaryIdentifier");
           */
          String geneSymbol = (String) protein.getFieldValue("symbol");
          InterMineObject organism = (InterMineObject) protein.getFieldValue("organism");
          String organismName = (String) organism.getFieldValue("name");

          /* In terms of activities, we want to know the type and concentration of
          * each of them */
          for( InterMineObject activity: activities){
            String type = (String) activity.getFieldValue("type");
            float concentration = (Float) activity.getFieldValue("conc");
            String unit = (String) activity.getFieldValue("unit");
            String relation = (String) activity.getFieldValue("relation");
            /* create a row with all the relevant information */
            //data.add(primaryAccession+"\t"+dbIdentifier+"\t"+organismName+"\t"+type+"\t"+concentration);
            if( unit.equals("nM") && relation.equals("=") ){
              data.add(primaryAccession+"\t"+geneSymbol+"\t"+organismName+"\t"+type+"\t"+concentration);
            }
          } // for activity
        }// if
      } // for interaction
      /* fill the resulting table with the corresponding values */
      request.setAttribute("compound", identifier);
      request.setAttribute("data", data);

    } // try
    catch(IllegalAccessException e){
      LOG.error(e.getMessage());
    }
	}
}
