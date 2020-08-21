package org.intermine.bio.dataconversion;

import java.io.File;

/*
 * Copyright (C) 2002-2019 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.ConstraintOp;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.ConstraintSet;
import org.intermine.objectstore.query.ContainsConstraint;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryCollectionReference;
import org.intermine.objectstore.query.QueryExpression;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.QueryValue;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.objectstore.query.SimpleConstraint;
import org.intermine.xml.full.Item;



/**
 * 
 * @author
 */
public class CtodConverter extends BioFileConverter
{
	private static final Logger LOG = LogManager.getLogger(CtodConverter.class);
	private File mrConsoFile;
	private File mrStyFile;

	//
	private static final String DATASET_TITLE = "COTD";
	private static final String DATA_SOURCE_NAME = "COTD";

	/**
	 * Constructor
	 * @param writer the ItemWriter used to handle the resultant items
	 * @param model the Model
	 */
	public CtodConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}
	private HashMap<String,String> trialGroupMap = new HashMap<>();
	private IdSetLoader trialGroupIdSet;
	private String osAlias = null;
	private static Map<String, String> trialPropertyNames = new HashMap<String, String>();

	static {

		trialPropertyNames.put("name", "COTD-${Reference ID}");
		trialPropertyNames.put("title", "${Title}");
		trialPropertyNames.put("phase", "${Clinical Phase}");
		trialPropertyNames.put("registryNumber", "${Trial registry number}");
		trialPropertyNames.put("inclusionCriteria", "${Inclusion criteria}");
		trialPropertyNames.put("exclusionnCriteria", "${Exclusion criteria}");
		trialPropertyNames.put("articleUrl", "${Link to article}");
		trialPropertyNames.put("countries", "${Countries}");
	}

	public void setOsAlias(String osAlias) {
		this.osAlias = osAlias;
	}

	private void createTrialGroup(String id,Item item) throws ObjectStoreException {
		if(trialGroupIdSet.hasId(id)) {
			String trialGroupRefId = trialGroupMap.get(id);
			if(trialGroupRefId==null) {
				Item trialGroup = createItem("TrialGroup");
				trialGroup.setAttribute("identifier", id);
				store(trialGroup);
				trialGroupRefId = trialGroup.getIdentifier();
				trialGroupMap.put(id, trialGroupRefId);
			}
			item.setReference("trialGroup", trialGroupRefId);
		}

	}
	private Map<String, String> publicationMap = new HashMap<String, String>();
	private String createPublication(String pubMedId) throws Exception {
		if (StringUtils.isEmpty(pubMedId)) {
			return "";
		}
		String ret = publicationMap.get(pubMedId);
		if (ret == null) {
			// Publication set only pubMedId.
			Item item = createItem("Publication");
			item.setAttribute("pubMedId", pubMedId);
			store(item);
			ret = item.getIdentifier();
			publicationMap.put(pubMedId, ret);
		}
		return ret;
	}
	private UMLSResolver resolver;
	public void setMrConsoFile(File mrConsoFile) {
		this.mrConsoFile = mrConsoFile;
	}

	public void setMrStyFile( File mrStyFile ) {
		this.mrStyFile = mrStyFile;
	}
	// key is CUI, value is reference to UmlsDisease item
	private Map<String, String> umlsTermMap = new HashMap<String, String>();

	public String createUMLSTerm(String cui) throws ObjectStoreException {
		String key = umlsTermMap.get(cui);
		if (key == null) {

			Item item = createItem("UMLSTerm");
			item.setAttribute("identifier", "UMLS:" + cui);
			store(item);
			umlsTermMap.put(cui, item.getIdentifier());
		}
		return key;
	}
	// key is CUI, value is reference to UmlsDisease item
	private Map<String, String> compoundMap = new HashMap<String, String>();

	public String createCompound(String name) throws Exception {
		if (!compoundMap.containsKey(name)) {
			String identifier = queryIdByName(name);
			if(identifier!=null) {
				Item item = createItem("Compound");

				item.setAttribute("identifier", identifier);
				store(item);
				compoundMap.put(name, item.getIdentifier());
			}else {
				compoundMap.put(name,null);
			}
		}
		return compoundMap.get(name);
	}
	private static final String COMPOUND_SYNONYM = "CompoundSynonym";

	@SuppressWarnings("unchecked")
	public String queryIdByName(String name) throws Exception {
		ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);
		Query q = new Query();
		QueryClass qcCompound = new QueryClass(os.getModel().getClassDescriptorByName("Compound").getType());
		QueryClass qcSynonym = new QueryClass(os.getModel().getClassDescriptorByName(COMPOUND_SYNONYM).getType());
		QueryField qfIdenfifier = new QueryField(qcCompound, "identifier");
		QueryField qfValue = new QueryField(qcSynonym, "value");
		q.addFrom(qcCompound);
		q.addFrom(qcSynonym);
		q.addToSelect(qfIdenfifier);

		ConstraintSet cs = new ConstraintSet(ConstraintOp.AND);

		QueryCollectionReference synRef = new QueryCollectionReference(qcCompound, "synonyms");
		cs.addConstraint(new ContainsConstraint(synRef, ConstraintOp.CONTAINS, qcSynonym));
		cs.addConstraint(new SimpleConstraint(new QueryExpression(QueryExpression.LOWER, qfValue), ConstraintOp.EQUALS, new QueryValue(name.toLowerCase())));

		cs.addConstraint(new SimpleConstraint(qfIdenfifier, ConstraintOp.IS_NOT_NULL));
		q.setConstraint(cs);
		Results results = os.execute(q);
		Iterator<Object> iterator = results.iterator();
		while(iterator.hasNext()) {
			ResultsRow<String> rr = (ResultsRow<String>) iterator.next();
			String id = rr.get(0);
			if(id!=null && id.length() > 0) {
				return id;
			}
		}
		return null;
	}

	/**
	 * 
	 *
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		if(resolver==null) {
			resolver = new UMLSResolver(mrConsoFile, mrStyFile);
		}

		if(trialGroupIdSet==null) {
			trialGroupIdSet = new IdSetLoader(osAlias, "TrialGroup", "identifier");
			trialGroupIdSet.loadIds();
		}
		ArrayList<Map<String,String>> list = new ArrayList<Map<String,String>>();
		Map<String, List<String>> headerGroups = null;
		try(CSVParser parser = new CSVParser(reader, true)){
			for (Map<String, String> map : parser) {
				list.add(map);
			}
			headerGroups =  parser.getHeaderGroups();

		}
		Collections.sort(list, new Comparator<Map<String,String>>() {
			@Override
			public int compare(Map<String, String> o1, Map<String, String> o2) {
				for(String key:new String[] {"Reference ID","Arm ID"}) {
					String lhs = o1.get(key);
					String rhs = o2.get(key);
					int compareTo = lhs.compareTo(rhs);
					if(compareTo!=0) {
						return compareTo;
					}
				}
				return 0;
			}
		});
		String prevId = null;
		String prevArmId = null;
		Item arm = null;
		Item item = null;
		StringBuilder primaryOutcome = null;
		StringBuilder secondaryOutcome = null;
		for(Map<String,String> row:list) {
			String refId = row.get("Reference ID");
			String armId = row.get("Arm ID");
			if(!refId.equals(prevId)) {
				if(arm!=null) {
					if(primaryOutcome!=null && 0 < primaryOutcome.length()) {
						arm.setAttribute("primaryOutcome", primaryOutcome.toString());
					}
					if(secondaryOutcome!=null && 0 < secondaryOutcome.length()) {
						arm.setAttribute("secondaryOutcome", secondaryOutcome.toString());
					}
					primaryOutcome = null;
					secondaryOutcome = null;
					store(arm);
					arm = null;
				}
				item = createItem("CTODTrial");
				String trialId = row.get("Trial registry number");
				String source = row.get("Source");
				if("PUBMED".equals(source)) {
					String pubmedId = row.get("Source ID");
					String publicationId = createPublication(pubmedId);
					item.setReference("publication", publicationId);
				}
				String cui = resolver.getIdentifier(row.get("Disease area"));
				String umlsTerm = createUMLSTerm(cui);
				if(umlsTerm != null) {
					item.setReference("umls", umlsTerm);
				}
				createTrialGroup(trialId, item);
				for (Entry<String,String> entry : trialPropertyNames.entrySet()) {
					String value = Utils.replaceString(entry.getValue(), row);
					if(value!=null && value.length()>0 && !"NA".equals(value)) {
						item.setAttribute(entry.getKey(), value);
					}
				}
				store(item);
			}
			prevId = refId;
			if(!armId.equals(prevArmId)) {
				if(arm!=null) {
					if(primaryOutcome!=null && 0 < primaryOutcome.length()) {
						arm.setAttribute("primaryOutcome", primaryOutcome.toString());
					}
					if(secondaryOutcome!=null && 0 < secondaryOutcome.length()) {
						arm.setAttribute("secondaryOutcome", secondaryOutcome.toString());
					}
					primaryOutcome = null;
					secondaryOutcome = null;
					store(arm);
				}
				arm = createItem("CTODTrialArm");
				arm.setReference("trial", item);
				arm.setAttribute("armId", armId);
				arm.setAttribute("name", row.get("Treatment Arm"));
				arm.setAttribute("control", row.get("Treatment Arm"));
				StringBuilder sb = new StringBuilder();
				for (String treatmentKey : new String[] {"Treatment1 Description","Treatment2 Description","Treatment other Description"}) {
					String treatments = row.get(treatmentKey);
					if("NA".equals(treatments)) {
						String[] split = treatments.split(";\\s?");
						for (String treatment : split) {
							String compooundRefId = createCompound(treatment);
							if(compooundRefId!=null) {
								arm.addToCollection("compounds", compooundRefId);
							}
						}
					}
				}
				if(!Utils.empty(sb.toString())){
					arm.setAttribute("treatment", sb.toString());
				}
				sb = new StringBuilder();
				for (String header : headerGroups.get("Patient Characterisitic Info.")) {
					String info = row.get(header);
					if(!Utils.empty(info) && !"NA".equals(info)) {
						if(sb.length()>0) {
							sb.append("; ");
						}
						sb.append(info+" "+header);
					}
				}
				if(!Utils.empty(sb.toString())){
					arm.setAttribute("patientCharacterisiticInfo", sb.toString());
				}
				for (String treatmentKey : new String[] {"Treatment1 name","Treatment2 name","Treatment other name"}) {
					String treatment = row.get(treatmentKey);
					if(!"NA".equals(treatment)) {
						String compooundRefId = createCompound(treatment);
						if(compooundRefId!=null) {
							arm.addToCollection("compounds", compooundRefId);
						}
					}
				}
			}
			String subGroup = row.get("Sub group");
			String outcomeClass = row.get("Outcome statistic");
			if("NA".equals(subGroup) && !outcomeClass.equals("KM curve")) {
				String type = row.get("Outcome type");
				StringBuilder sb2 = null;
				if("secondary".equalsIgnoreCase(type)) {
					if(secondaryOutcome==null) {
						secondaryOutcome = new StringBuilder();
					}
					sb2 = secondaryOutcome;
				}else {
					if(primaryOutcome==null) {
						primaryOutcome = new StringBuilder();
					}
					sb2 = primaryOutcome;
				}
				if(sb2.length()>0) {
					sb2.append(" ; ");
				}
				sb2.append(row.get("Outcome Short Form") +" " + row.get("Outcome point estimate")+" " + row.get("Outcome point estimate unit"));
				String variabilityMeasure = row.get("Outcome variability measure");
				if(!"NA".equals(variabilityMeasure)) {
					sb2.append(" " + variabilityMeasure + " " +row.get("Outcome variability lower limit")+" - "+row.get("Outcome varibility upper limit"));
				}
				String pValue = row.get("Outcome p value");
				if(!"NA".equals(pValue)) {
					if(pValue.startsWith("<")) {
						sb2.append(" (p " + pValue+")");
					}else {
						sb2.append(" (p =" + pValue +")");								
					}
				}

			}
			prevArmId = armId;
		}
	}
}
