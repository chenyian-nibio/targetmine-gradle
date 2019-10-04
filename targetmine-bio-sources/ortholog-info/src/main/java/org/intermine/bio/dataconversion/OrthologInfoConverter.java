package org.intermine.bio.dataconversion;

import java.io.Reader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.ConstraintOp;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.BagConstraint;
import org.intermine.objectstore.query.ConstraintSet;
import org.intermine.objectstore.query.ContainsConstraint;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.QueryObjectReference;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 * 
 * @author chenyian
 */
public class OrthologInfoConverter extends BioFileConverter {
	private static final Logger LOG = LogManager.getLogger(OrthologInfoConverter.class);
	//
	private static final String DATASET_TITLE = "Gene";
	private static final String DATA_SOURCE_NAME = "NCBI";

	private Set<String> taxonIds = new HashSet<String>();

	public void setOrthologOrganisms(String idListString) {
		String[] taxonStringIds = StringUtils.split(idListString, " ");
		for (String string : taxonStringIds) {
			this.taxonIds.add(string);
		}
		LOG.info("Setting list of organisms to " + this.taxonIds);
		System.out.println("Setting list of organisms to " + this.taxonIds);
	}

	/**
	 * Constructor
	 * 
	 * @param writer
	 *            the ItemWriter used to handle the resultant items
	 * @param model
	 *            the Model
	 */
	public OrthologInfoConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * 
	 *
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		if (geneIds.isEmpty()) {
			getGeneIds();
			LOG.info("Found " + geneIds.size() + " gene ids.");
		}
		
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		int count = 0;
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			String taxonId = cols[0].trim();
			String geneId = cols[1].trim();
			String symbol = cols[2].trim();
			String type = cols[9].trim();
			String name = cols[11].trim();
			
			if (geneIds.contains(geneId)) {
				Item item = createItem("Gene");
				item.setAttribute("primaryIdentifier", geneId);
				item.setAttribute("ncbiGeneId", geneId);
				item.setAttribute("symbol", symbol);
				item.setReference("organism", getOrganism(taxonId));
				item.setAttribute("type", type);
				
				if (name != null && !name.equals("-")) {
					item.setAttribute("name", name);
				}
				store(item);
				count++;
			}
		}
		LOG.info(String.format("%d genes have been updated.", count));
		
	}

	private String osAlias = null;

	public void setOsAlias(String osAlias) {
		this.osAlias = osAlias;
	}

	private Set<String> geneIds = new HashSet<String>();

	@SuppressWarnings("unchecked")
	protected void getGeneIds() throws Exception {
		ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);

		Query q = new Query();
		QueryClass qcGene = new QueryClass(
				os.getModel().getClassDescriptorByName("Gene").getType());
		QueryClass qcOrganism = new QueryClass(
				os.getModel().getClassDescriptorByName("Organism").getType());

		QueryField qfGeneId = new QueryField(qcGene, "primaryIdentifier");
		QueryField qfOrganismTaxonId = new QueryField(qcOrganism, "taxonId");

		q.addFrom(qcGene);
		q.addFrom(qcOrganism);
		q.addToSelect(qfGeneId);

		ConstraintSet cs = new ConstraintSet(ConstraintOp.AND);
		cs.addConstraint(new BagConstraint(qfOrganismTaxonId, ConstraintOp.IN, taxonIds));
		QueryObjectReference qor = new QueryObjectReference(qcGene, "organism");
		cs.addConstraint(new ContainsConstraint(qor, ConstraintOp.CONTAINS, qcOrganism));

		q.setConstraint(cs);

		Results results = os.execute(q);
		Iterator<Object> iterator = results.iterator();
		while (iterator.hasNext()) {
			ResultsRow<String> rr = (ResultsRow<String>) iterator.next();
			geneIds.add(rr.get(0));
		}
	}

}
