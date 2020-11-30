package org.intermine.bio.web.displayer;

import java.util.ArrayList;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.intermine.api.InterMineAPI;
import org.intermine.model.InterMineObject;
import org.intermine.model.bio.Gene;
import org.intermine.model.bio.ProbeSet;
import org.intermine.model.bio.Expression;
import org.intermine.model.bio.HbiExpression;
import org.intermine.model.bio.Tissue;
import org.intermine.model.bio.HbiTissue;

import org.intermine.web.displayer.ReportDisplayer;
import org.intermine.web.logic.config.ReportDisplayerConfig;
import org.intermine.web.logic.results.ReportObject;

/**
 * Class used for retrieval and handling of information used in the display
 * of an Expression level graph display for Genes in Targetmine
 *
 * @author Rodolfo Allendes
 * @version 0.1
 */
public class GeneExpressionGraphDisplayer extends ReportDisplayer{
  /* define a LOG to post messages to */
  protected static final Logger logger = Logger.getLogger(GeneExpressionGraphDisplayer.class);

  /**
   * Constructor
   * Use super class to initialize required components
   */
  public GeneExpressionGraphDisplayer(ReportDisplayerConfig config, InterMineAPI im){
    super(config,im);
  }

  /**
   *
   * @param request
   * @param reportObject
   */
  @SuppressWarnings("unchecked")
  @Override
  public void display(HttpServletRequest request, ReportObject reportObject){

    // A list of data elements that we will forward to Javascript for the
    // definition of the graph
    ArrayList<String> data = new ArrayList<String>();
    String header = "probeSetId\t"+
      "category\t"+
      "organ\t"+
      "name\t"+
      "call\t"+
      "value";
    String row; // we will use this to add elements to the data array
    data.add(header);

    // logger.error("header\n"+header);
    // The data retrieved from the database
    Gene gene = (Gene) reportObject.getObject();
    // InterMineObject gene = (InterMineObject) reportObject.getObject();

    try{
      // A gene has a collection of ProbeSet objects associated, we need to
      // process each probe in order to display its values
      Set<ProbeSet> probeSets = (Set<ProbeSet>) gene.getFieldValue("probeSets");
      for( ProbeSet ps: probeSets ){

        // Each probeSet has a collection of Expression objects associated, we
        // process each individually
        Set<Expression> expressions = (Set<Expression>) ps.getFieldValue("expressions");
        for( Expression exp: expressions ){
        	//logger.error("class name: " + exp.getClass().getName());	
        	if (!exp.getClass().getName().equals("org.intermine.model.bio.HbiExpressionShadow")) {
        		continue;
        	}

          String probeID = (String)ps.getFieldValue("probeSetId");
          // For each expression value, we need to store the following information
          // tissue (hbiTissue)
          // |-- category (String)
          // |-- organ (String)
          // |-- name (String)
          // call (String <enum>['P', 'A', 'M'])
          // value (float)
          Tissue tissue = (HbiTissue) exp.getFieldValue("tissue");
          String category = (String) tissue.getFieldValue("category");
          String organ = (String) tissue.getFieldValue("organ");
          String name = (String) tissue.getFieldValue("name");
          // the char '/' cant be part of css selectors, so we replace it for '-'
          category = category.replaceAll("/","-");
          organ = organ.replaceAll("/","-");
          name = name.replaceAll("/","-");

          String call = (String) exp.getFieldValue("call");
          float value = (Float) exp.getFieldValue("value");

          row = probeID+"\t";
          row += (category+"\t");
          row += (organ+"\t");
          row += (name+"\t");
          row += (call+"\t");
          row += value;

          data.add(row);
        }

      }
      /* fill the resulting table with the data */
      request.setAttribute("gene", (String) gene.getFieldValue("ncbiGeneId"));
      request.setAttribute("data", data);

    } //try
    catch(IllegalAccessException e){
      logger.error(e.getMessage());
    }
  }
}
