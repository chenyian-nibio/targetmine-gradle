package org.intermine.bio.dataconversion;

import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 * 
 * @author chenyian
 */
public class MirtarbaseConverter extends BioFileConverter {
	private static Logger LOG = Logger.getLogger(MirtarbaseConverter.class);
	//
	private static final String DATASET_TITLE = "miRTarBase";
	private static final String DATA_SOURCE_NAME = "miRTarBase";

	private Map<String, String> geneMap = new HashMap<String, String>();
	private Map<String, String> miRNAMap = new HashMap<String, String>();
	private Map<String, String> publicationMap = new HashMap<String, String>();
	private Map<String, String> experimentMap = new HashMap<String, String>();
	private Map<String, String> interactionMap = new HashMap<String, String>();

	/**
	 * Constructor
	 * 
	 * @param writer
	 *            the ItemWriter used to handle the resultant items
	 * @param model
	 *            the Model
	 */
	public MirtarbaseConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * 
	 * 
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		if (miRNAIdMap == null) {
			getMiRNAIdMaps();
		}

		Set<String> unfound = new HashSet<String>();
		int count = 0;

		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			String sourceId = cols[0];
			String symbol = cols[1];
			String ncbiGeneId = cols[2];
			String experiment = cols[3];
			String supportType = cols[4];
			String pubmedId = cols[5];
			
			if (ncbiGeneId.equals("0")) {
				continue;
			}

			if (miRNAIdMap.get(symbol) == null) {
				LOG.info("cannot find the miRNA symbol: " + symbol);
				// System.out.println("cannot find the miRNA symbol: " + symbol);
				unfound.add(symbol);
			} else {
				for (String pid : miRNAIdMap.get(symbol)) {
					createMiRNAEvidence(sourceId, ncbiGeneId, experiment, supportType, pubmedId, pid);
					count++;
				}
			}

		}

		System.out.println("create " + count + " miRNA interactions.");
		System.out.println("Undfound entities: " + unfound);
	}

	private void createMiRNAEvidence(String sourceId, String ncbiGeneId, String experiment, String supportType,
			String pubmedId, String mirnaId) throws ObjectStoreException {
		Item item = createItem("MiRNAEvidence");

		item.setReference("interaction", getMiRNAInteraction(mirnaId, ncbiGeneId, sourceId, supportType));
		item.setReference("publication", getPublication(pubmedId));

		if (!StringUtils.isEmpty(experiment) && !experiment.equals("-")) {
			Set<String> expSet = new HashSet<String>(Arrays.asList(experiment.split("//|;")));

			for (String exp : expSet) {
				if (!StringUtils.isEmpty(exp)) {
					item.addToCollection("experiments", getMiRNAExperiment(exp));
				}
			}
		}
		store(item);
	}

	private String getMiRNAInteraction(String miRNAAcc, String geneId, String sourceId,
			String supportType) throws ObjectStoreException {
		String identifier = miRNAAcc + "-" + geneId;

		String ret = interactionMap.get(identifier);

		if (ret == null) {
			Item item = createItem("MiRNAInteraction");

			item.setAttribute("identifier", identifier);
			item.setAttribute("sourceId", sourceId);
			item.setAttribute("supportType", supportType);
			item.setReference("targetGene", getGene(geneId));
			item.setReference("miRNA", getMiRNA(miRNAAcc));

			store(item);
			ret = item.getIdentifier();
			interactionMap.put(identifier, ret);
		}

		return ret;

	}

	private String getGene(String ncbiGeneId) throws ObjectStoreException {
		String ret = geneMap.get(ncbiGeneId);
		if (ret == null) {
			Item item = createItem("Gene");
			item.setAttribute("primaryIdentifier", ncbiGeneId);
			item.setAttribute("ncbiGeneId", ncbiGeneId);
			store(item);
			ret = item.getIdentifier();
			geneMap.put(ncbiGeneId, ret);
		}
		return ret;
	}

	private String getMiRNA(String accession) throws ObjectStoreException {
		String ret = miRNAMap.get(accession);
		if (ret == null) {
			Item item = createItem("MiRNA");
			item.setAttribute("primaryIdentifier", accession);
			store(item);
			ret = item.getIdentifier();
			miRNAMap.put(accession, ret);
		}
		return ret;
	}

	private String getMiRNAExperiment(String name) throws ObjectStoreException {
		String ret = experimentMap.get(name);
		if (ret == null) {
			Item item = createItem("MiRNAExperiment");
			item.setAttribute("name", name);
			store(item);
			ret = item.getIdentifier();
			experimentMap.put(name, ret);
		}
		return ret;
	}

	private String getPublication(String pubmedId) throws ObjectStoreException {
		String ret = publicationMap.get(pubmedId);
		if (ret == null) {
			Item item = createItem("Publication");
			item.setAttribute("pubMedId", pubmedId);
			store(item);
			ret = item.getIdentifier();
			publicationMap.put(pubmedId, ret);
		}
		return ret;
	}

	private String osAlias = null;

	public void setOsAlias(String osAlias) {
		this.osAlias = osAlias;
	}
	
	private Map<String, Set<String>> miRNAIdMap;

	@SuppressWarnings("unchecked")
	private void getMiRNAIdMaps() throws Exception {
		ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);

		Query q = new Query();
		QueryClass qcSnp = new QueryClass(os.getModel().getClassDescriptorByName("MiRNA").getType());

		QueryField qfPrimaryId = new QueryField(qcSnp, "primaryIdentifier");
		QueryField qfSymbol = new QueryField(qcSnp, "symbol");

		q.addFrom(qcSnp);
		q.addToSelect(qfPrimaryId);
		q.addToSelect(qfSymbol);

		Results results = os.execute(q);
		Iterator<Object> iterator = results.iterator();
		miRNAIdMap = new HashMap<String, Set<String>>();
		while (iterator.hasNext()) {
			ResultsRow<String> rr = (ResultsRow<String>) iterator.next();
			String symbol = rr.get(1);
			if (StringUtils.isEmpty(symbol)) {
				continue;
			}
			if (miRNAIdMap.get(symbol) == null) {
				miRNAIdMap.put(symbol, new HashSet<String>());
			}
			miRNAIdMap.get(symbol).add(rr.get(0));
		}
	}

}
