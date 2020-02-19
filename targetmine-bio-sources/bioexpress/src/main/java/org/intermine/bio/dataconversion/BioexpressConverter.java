package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;


/**
 * 
 * @author
 */
public class BioexpressConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "Add DataSet.title here";
    private static final String DATA_SOURCE_NAME = "Add DataSource.name here";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public BioexpressConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }
    private String sampleTreatmentDescriptionPrefix = "Sample Treatment:  ";
    private String[] sampleTreatmentDescriptionNames = new String[]{
		"Time Reported Value",
		"Dose Reported Value",
		"Dose Regimen",
		"Treatment Description",
		"Vehicle Ingredient",
		"Vehicle Solvent",
		"Vehicle Concentration Reported Value",
		"Treatment Comments",
    };

    private static String convertToDescription(Map<String,String> entry,String prefix,String[] names) {
    	StringBuffer sb = new StringBuffer();
    	for (String name : names) {
			String value = entry.get(prefix+name);
			if(!Utils.isEmpty(value)) {
				if(sb.length()>0) {
					sb.append(". ");
				}
				sb.append(name);
				sb.append(": ");
				sb.append(value);
			}
		}
    	return sb.toString();
    }
    private int[] getHeaderIndexWithPrefix(String prefix,String[] headers) {
    	ArrayList<Integer> ids = new ArrayList<>();
    	for (int i = 0; i < headers.length; i++) {
			if(headers[i].startsWith(prefix)) {
				
			}
		}
    	int[] id = new int[ids.size()];
    	for (int i = 0; i < id.length; i++) {
			id[i] = ids.get(i);
		}
    	return id;
    }
    private static String convertToDescription(Map<String,String> entry,String prefix,String[] headers,int[] indexes) {
    	StringBuffer sb = new StringBuffer();
    	for (int index : indexes) {
			String key = headers[index];
			String value = entry.get(key);
			if(!Utils.isEmpty(value)) {
				if(sb.length()>0) {
					sb.append(". ");
				}
				sb.append(key.substring(prefix.length()));
				sb.append(": ");
				sb.append(value);
			}
		}
    	return sb.toString();
    }
	private static HashMap<String,String> donorProperties = new HashMap<>();
	private static HashMap<String,String> sampleProperties = new HashMap<>();
    static {
    	HashMap<String,String> m = new HashMap<>();
        m.put("Donor ID","identifier"); //4800
        m.put("Gender","gender"); //4800
        m.put("Race/Ethnicity","ethnicity"); //4798
        //m.put("Age at Death in d",""); //123
        //m.put("Age at Death in hr",""); //123
        //m.put("Age at Death in mo",""); //123
        //m.put("Age at Death in wk",""); //123
        //m.put("Age at Death in yr",""); //123
        m.put("Age at Death Reported Value","ageAtDeath"); //126
        m.put("Death Cause","deathCause"); //126
        m.put("Donor Comments","comment"); //939
		donorProperties = m;
		
		m = new HashMap<>();
        m.put("Genomics ID","identifier"); //4800
        m.put("Sample Type","sampleType"); //4800
        m.put("Sample Site","sampleSite"); //4800
        m.put("Pathology/Morphology","pathology"); //4513
        m.put("Sample Specific Pathologic Type","pathologicType"); //4513
        m.put("General Pathologic Category","generalPathologicCategory"); //4800
        m.put("At Sample Time:  Primary Site","primarySite"); //4800
        m.put("At Sample Time: Primary  Donor Primary Disease","primaryDisease"); //4800
        m.put("Pathology QC Report","qcReport"); //4791
        m.put("General Sample Description","description"); //4799
        m.put("Autopsy Tissue?","autopsy"); //4800
        //m.put("Relationship","relatoinship"); //2858
        //m.put("Related Samples","relatedSamples"); //2858
        m.put("Sample Relationship Attribute Set - Relationship, Related Sample","relationShips"); //2858
        m.put("Sample Set Name","sampleSetName"); //2348
        m.put("Sample ID","sampleId"); //4800
        m.put("Experiment Name - HG-U133_Plus_2","experimentName"); //4800
        sampleProperties = m;
        //m.put("Sample Species","spec"); //4800
    }
	private static HashMap<String,String> donorRef = new HashMap<>();
    private String createDonorRef(Map<String,String> entry) throws ObjectStoreException {
    	String donorId = entry.get("Donor ID");
    	if(Utils.isEmpty(donorId)) {
    		return null;
    	}
    	if(donorRef.containsKey(donorId)) {
    		return donorRef.get(donorId);
    	}
    	Item donor = createItem("BioExpressDonor");
    	for (Entry<String, String> e : donorProperties.entrySet()) {
			String value = entry.get(e.getKey());
			if(!Utils.isEmpty(value)) {
				donor.setAttribute(e.getValue(), value);
			}
		}
    	store(donor);
    	donorRef.put(donorId, donor.getIdentifier());
    	return donor.getIdentifier();
    }
	private static HashMap<String,String> sampleRef;
    private void createSampleRef(Map<String,String> entry) throws ObjectStoreException {
    	String genomicId = entry.get("Genomics ID");
    	String exerimentName = entry.get("Experiment Name - HG-U133_Plus_2");
    	Item sample = createItem("BioExpressSample");
    	for (Entry<String, String> e : sampleProperties.entrySet()) {
			String value = entry.get(e.getKey());
			if(!Utils.isEmpty(value)) {
				sample.setAttribute(e.getValue(), value);
			}
		}
    	String donorRef = createDonorRef(entry);
    	if(!Utils.isEmpty(donorRef)) {
    		sample.setReference("donor", donorRef);
    	}
    	store(sample);
    	sampleRef.put(exerimentName, sample.getIdentifier());
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
    	if(sampleRef == null) {
    		sampleRef = new HashMap<String, String>();
    		try(CSVParser parser = new CSVParser(new FileReader(annotationCsvFile), false)){
    			for (Map<String, String> entry : parser) {
    				createSampleRef(entry);
    			}
    		}
    	}
    	ItemCreator probeSetCreator = new ItemCreator(this,"ProbeSet","primaryIdentifier");
    	BufferedReader bufreader = new BufferedReader(reader);
    	String line = bufreader.readLine();
    	String[] filenames = line.split("\t");
    	String[] experimantNames = new String[filenames.length];
    	String[] sampleRefs = new String[filenames.length];
    	for (int i = 1; i < filenames.length; i++) {
    		experimantNames[i] = filenames[i].replaceFirst("\\.cel$", "").toUpperCase();
    		sampleRefs[i] = sampleRef.get(experimantNames[i]);
		}
    	while((line = bufreader.readLine()) != null) {
    		String[] split = line.split("\t");
    		String probeId = split[0];
			String probeIdRef = probeSetCreator.createItemRef(probeId);
    		for (int i = 1; i < split.length; i++) {
				Item item = createItem("BioExpressExpression");
				item.setAttribute("value", split[i]);
				item.setReference("sample", sampleRefs[i]);
				item.setReference("probeSet", probeIdRef);
	    		store(item);
			}
    	}
    }
	private File annotationCsvFile;
	public void setAnnotationCsvFile(File annotationCsvFile) {
		this.annotationCsvFile = annotationCsvFile;
	}
}
