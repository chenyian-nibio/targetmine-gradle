package org.intermine.bio.web.displayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.intermine.api.InterMineAPI;
import org.intermine.model.InterMineObject;
import org.intermine.model.bio.Gene;
import org.intermine.model.bio.Protein;
import org.intermine.web.displayer.ReportDisplayer;
import org.intermine.web.logic.config.ReportDisplayerConfig;
import org.intermine.web.logic.results.ReportObject;

public class HomologInfoDisplayer extends ReportDisplayer {
	protected static final Logger LOG = Logger.getLogger(HomologInfoDisplayer.class);
	private static final Map<Integer, Integer> ORGANISM_ORDER_MAP = new HashMap<Integer, Integer>();
	{
		ORGANISM_ORDER_MAP.put(9606, 1);
		ORGANISM_ORDER_MAP.put(10116, 2);
		ORGANISM_ORDER_MAP.put(10090, 3);
		ORGANISM_ORDER_MAP.put(9483, 4);
		ORGANISM_ORDER_MAP.put(9541, 5);
		ORGANISM_ORDER_MAP.put(9544, 6);
		ORGANISM_ORDER_MAP.put(9615, 7);
		ORGANISM_ORDER_MAP.put(9986, 8);
		ORGANISM_ORDER_MAP.put(7955, 9);
		ORGANISM_ORDER_MAP.put(7227, 10);
	}
	private static final Map<String, String> DATA_SET_DISPLAY_NAME_MAP = new HashMap<String, String>();
	{
		DATA_SET_DISPLAY_NAME_MAP.put("Gene", "Orthologs from Annotation Pipeline");
		DATA_SET_DISPLAY_NAME_MAP.put("KEGG Orthology", "KEGG Orthology (KO)");
		DATA_SET_DISPLAY_NAME_MAP.put("HomoloGene", "HomoloGene");
	}
	private static final Map<String, String> DATA_SET_DISPLAY_COLOR_MAP = new HashMap<String, String>();
	{
		DATA_SET_DISPLAY_COLOR_MAP.put("Gene", "#336699");
		DATA_SET_DISPLAY_COLOR_MAP.put("KEGG Orthology", "#A70158");
		DATA_SET_DISPLAY_COLOR_MAP.put("HomoloGene", "#669933");
	}

//	private static final String GENE_DATASET_TITLE = "Orthologs from Annotation Pipeline";
	
	public HomologInfoDisplayer(ReportDisplayerConfig config, InterMineAPI im) {
		super(config, im);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void display(HttpServletRequest request, ReportObject reportObject) {
		Gene gene = (Gene) reportObject.getObject();
		Map<String, Gene> allOrthologs = new HashMap<String, Gene>();
		try {
			// bi-directional best hit
			Set<Protein> proteins = gene.getProteins();
			for (Protein protein : proteins) {
				Set<Protein> orthologs = protein.getOrthologProteins();
				for (Protein ortholog : orthologs) {
					Set<Gene> genes = ortholog.getGenes();
					for (Gene entry : genes) {
						allOrthologs.put(entry.getPrimaryIdentifier(), entry);
					}
				}
			}
			request.setAttribute("orthologs", allOrthologs.values());

			// homology
			Set<InterMineObject> homology = (Set<InterMineObject>) gene.getFieldValue("homology");
			Map<InterMineObject, List<String>> dataSetMap = new HashMap<InterMineObject, List<String>>();
			for (InterMineObject entry : homology) {
				Set<Gene> genes = (Set<Gene>) entry.getFieldValue("genes");
				String dataSetName = (String) ((InterMineObject) entry.getFieldValue("dataSet"))
						.getFieldValue("name");
				for (Gene ortholog : genes) {
					// itself, skip
					if (gene.getPrimaryIdentifier().equals(ortholog.getPrimaryIdentifier())) {
						continue;
					}
					if (dataSetMap.get(ortholog) == null) {
						dataSetMap.put(ortholog, new ArrayList<String>());
					}
					dataSetMap.get(ortholog).add(dataSetName);
				}
			}
			Map<InterMineObject, String> retHomologyMap = new HashMap<InterMineObject, String>();
			for (InterMineObject ortholog : dataSetMap.keySet()) {
				List<String> dataSetList = dataSetMap.get(ortholog);
				Collections.sort(dataSetList);
				StringBuffer sb = new StringBuffer();
				for (String ds : dataSetList) {
					String displayName = ds;
					if (DATA_SET_DISPLAY_NAME_MAP.get(ds) != null) {
						displayName = DATA_SET_DISPLAY_NAME_MAP.get(ds);
					}
					String displaycolor = "#333333";
					if (DATA_SET_DISPLAY_COLOR_MAP.get(ds) != null) {
						displaycolor = DATA_SET_DISPLAY_COLOR_MAP.get(ds);
					}
					String initial = ds.substring(0, 1).toUpperCase();
	    			sb.append(String.format("<span style=\"padding: 0 2px; color: white; background-color: %s; "
	    					+ "font-weight: bold;\" title=\"%s\">%s</span>&nbsp;", displaycolor, displayName, initial));
				}
//				retHomologyMap.put(ortholog, StringUtils.join(dataSetList, " / "));
				retHomologyMap.put(ortholog, sb.toString());
			}
			List<InterMineObject> retHomologyList = new ArrayList<InterMineObject>(
					dataSetMap.keySet());

			// sort by preset organism order than gene id
			Collections.sort(retHomologyList, new Comparator<InterMineObject>() {

				@Override
				public int compare(InterMineObject o1, InterMineObject o2) {
					try {
						Integer taxonId1 = (Integer) ((InterMineObject) o1
								.getFieldValue("organism")).getFieldValue("taxonId");
						Integer taxonId2 = (Integer) ((InterMineObject) o2
								.getFieldValue("organism")).getFieldValue("taxonId");
						LOG.info(String.format("taxonId1: %d, taxonId2: %d", taxonId1, taxonId2));
						LOG.info(String.format("taxonId1_idx: %d, taxonId2_idx: %d",
								ORGANISM_ORDER_MAP.get(taxonId1),
								ORGANISM_ORDER_MAP.get(taxonId2)));
						if (ORGANISM_ORDER_MAP.get(taxonId1)
								.equals(ORGANISM_ORDER_MAP.get(taxonId2))) {
							return taxonId1.compareTo(taxonId2);
						} else {
							return ORGANISM_ORDER_MAP.get(taxonId1)
									.compareTo(ORGANISM_ORDER_MAP.get(taxonId2));
						}
					} catch (IllegalAccessException e) {
						LOG.error(e.getMessage());
					}
					return 0;
				}

			});

			request.setAttribute("retHomologyMap", retHomologyMap);
			request.setAttribute("retHomologyList", retHomologyList);

		} catch (IllegalAccessException e) {
			LOG.error(e.getMessage());
		}

	}

}
