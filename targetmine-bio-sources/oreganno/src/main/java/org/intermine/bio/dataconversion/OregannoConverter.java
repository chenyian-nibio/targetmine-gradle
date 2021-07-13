package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 * 
 * @author chenyian
 */
public class OregannoConverter extends BioFileConverter {
	// private static final Logger LOG = Logger.getLogger(OregannoConverter.class);

	//
	private static final String DATASET_TITLE = "ORegAnno";
	private static final String DATA_SOURCE_NAME = "ORegAnno";

	private static final Map<String, String> TAXON_ID_MAP = new HashMap<String, String>();
	{
		TAXON_ID_MAP.put("Homo sapiens", "9606");
		TAXON_ID_MAP.put("Mus musculus", "10090");
		TAXON_ID_MAP.put("Rattus norvegicus", "10116");
	}

	private Map<String, String> geneMap = new HashMap<String, String>();

	/**
	 * Constructor
	 * 
	 * @param writer
	 *            the ItemWriter used to handle the resultant items
	 * @param model
	 *            the Model
	 */
	public OregannoConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * 
	 * 
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(new BufferedReader(reader));

		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			// source target sequence start end chromosome strand genomeBuild stableId
			String sourceId = cols[19].trim();
			String targetId = cols[18].trim();
			if (!isValidId(sourceId)) {
				continue;
			}
			if (!isValidId(targetId)) {
				continue;
			}
			
			String taxonID = TAXON_ID_MAP.get(cols[1].trim());
			
			Item bindingStie = createItem("TFBindingSite");
			bindingStie.setAttribute("primaryIdentifier", cols[8]);
			bindingStie.setAttribute("name", String.format("TF %s target %s", sourceId, targetId));
//			bindingStie.setAttribute("length", String.valueOf(cols[2].length()));
//			bindingStie.setReference("sequence", createSequence(cols[2]));
			bindingStie.setReference("organism", getOrganism(taxonID));
			
			
			if (!cols[14].equals("N/A") && !cols[15].equals("N/A") && !cols[16].equals("N/A")
					&& !cols[17].equals("N/A")) {
				String chr = cols[15].substring(3);
				String chromosomeRefId = getChromosome(chr, taxonID);

				Item location = createItem("Location");
				location.setAttribute("start", cols[16].trim());
				location.setAttribute("end", cols[17].trim());
				String strand = "+";
				if (cols[14].startsWith("-")) {
					strand = "-";
				}
				location.setAttribute("strand", strand);
				// TODO this part is not clear, comment out temporary...
//				location.setReference("feature", getGene(targetId));
				location.setReference("locatedOn", chromosomeRefId);
				store(location);
				
				bindingStie.setReference("chromosome", chromosomeRefId);
				bindingStie.setReference("chromosomeLocation", location);
			}
			store(bindingStie);

			Item interaction = getInteraction(sourceId, targetId);
			interaction.addToCollection("bindingSites", bindingStie);
			String pubmedId = cols[11].trim();
			if (isValidId(pubmedId)) {
				interaction.addToCollection("publications", getPublication(pubmedId));
			}

		}
		reader.close();
	}

	Map<String, Item> interactionMap = new HashMap<String, Item>();

	private Item getInteraction(String tfGeneId, String targetGeneId) throws ObjectStoreException {
		String key = tfGeneId + "->" + targetGeneId;
		Item ret = interactionMap.get(key);
		if (ret == null) {
			ret = createItem("TranscriptionalRegulation");
			ret.setAttribute("name", key);
			ret.setReference("transcriptionFactor", getGene(tfGeneId));
			ret.setReference("targetGene", getGene(targetGeneId));

			interactionMap.put(key, ret);
		}
		return ret;
	}

	private String getGene(String primaryIdentifier) throws ObjectStoreException {
		String ret = geneMap.get(primaryIdentifier);
		if (ret == null) {
			Item item = createItem("Gene");
			item.setAttribute("primaryIdentifier", primaryIdentifier);
			store(item);
			ret = item.getIdentifier();
			geneMap.put(primaryIdentifier, ret);
		}
		return ret;
	}

	private Map<String, String> chromosomeMap = new HashMap<String, String>();

	private String getChromosome(String chr, String taxonId) throws ObjectStoreException {
		String key = chr + ":" + taxonId;
		String ret = chromosomeMap.get(key);
		if (ret == null) {
			Item item = createItem("Chromosome");
			String chrId = chr;
			if (chr.toLowerCase().startsWith("chr")) {
				chrId = chr.substring(3);
			}
			item.setAttribute("primaryIdentifier", chrId);
			item.setAttribute("symbol", chrId);
			item.setReference("organism", getOrganism(taxonId));
			store(item);
			ret = item.getIdentifier();
			chromosomeMap.put(key, ret);
		}
		return ret;
	}

	private Map<String, String> publicationMap = new HashMap<String, String>();

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


//	private String createSequence(String sequence) throws ObjectStoreException {
//		Item item = createItem("Sequence");
//		item.setAttribute("residues", sequence);
//		item.setAttribute("length", String.valueOf(sequence.length()));
//		store(item);
//		return item.getIdentifier();
//	}

	@Override
	public void close() throws Exception {
		store(interactionMap.values());
	}

	/**
	 * check if the string s is a positive integer 
	 * @param s
	 * @return
	 */
	public static boolean isValidId(String s) {
		if (s == null || s.isEmpty())
			return false;
		for (int i = 0; i < s.length(); i++) {
			if (i == 0 && s.charAt(i) == '0') {
				return false;
			}
			if (Character.digit(s.charAt(i), 10) < 0)
				return false;
		}
		return true;
	}

}
