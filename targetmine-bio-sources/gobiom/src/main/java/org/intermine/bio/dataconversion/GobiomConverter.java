package org.intermine.bio.dataconversion;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.inchi.InChIGenerator;
import org.openscience.cdk.inchi.InChIGeneratorFactory;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.smiles.SmilesParser;

import net.sf.jniinchi.INCHI_RET;
/**
 * @author Ishikawa.Motokazu
 */
public class GobiomConverter extends BioFileConverter {
	
	private static final Logger LOG = LogManager.getLogger( GobiomConverter.class );
	
	private static final String DATASET_TITLE = "GOBIOM";
	private static final String DATA_SOURCE_NAME = "GOBIOM";
	
	private static final String HOMO_SAPIENS_TAXON_ID = "9606";
	
	private File humanIdMappingFile;
	private File locusMasterFile;
	private File mrConsoFile;
	
	// key is biomarker name, value is Biomarker item
	private Map<String, Item> biomarkerMap = new HashMap<String, Item>();
	// key is compound biomarker name, value is Compound Item
	private Map<String, Item> compoundMap = new HashMap<String, Item>();
	// key is inchikey, value is Compound Group
	private Map<String, Item> compoundGroupMap = new HashMap<String, Item>();
	// key is CUI, value is reference to DiseaseTerm item
	private Map<String, String> diseaseTermMap = new HashMap<String, String>();
	// key is gene symbol, value is reference to Gene item
	private Map<String, String> geneMap = new HashMap<String, String>();
	// key is locs_id, value is gene symbol
	private Map<String, String> locusMap = new HashMap<String, String>();
	// key is string of source vocabularies, value is CUI
	private Map<String, String> mrConsoMap = new HashMap<String, String>();
	// key is protein UniProt Accession, value is reference to Protein item
	private Map<String, String> proteinMap = new HashMap<String, String>();
	// key is gene symbol, value is UniProt Accession
	private Map<String, String> symbolUniProtMap = new HashMap<String, String>();

	/**
	 * Construct a new GobiomConverter.
	 * 
	 * @param model
	 *            the Model used by the object store we will write to with the ItemWriter
	 * @param writer
	 *            an ItemWriter used to handle Items created
	 */
	public GobiomConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		
		LOG.info("Start to process GOBIOM");
		/**
		 * Processing MRCONSO.RRF file to collect UMLS's source vocabularies and CUIs
		 */
		Iterator<String[]> mrConsoIterator = getMrConsoIterator();
		mrConsoIterator.next(); // Skip header
		while( mrConsoIterator.hasNext() ) {
			
			String[] mrConsoRow = mrConsoIterator.next();
			String cui = mrConsoRow[ 0 ];
			String str = mrConsoRow[ 14 ];
			mrConsoMap.put( str.toLowerCase(), cui );
			
		}
		
		/**
		 * Processing HUMAN_9606_idmapping.dat file to collect gene symbol-UniProt Accession relationship
		 */
		Iterator<String[]> humanIdMappingIterator = getHumanIdMappingIterator();
		while( humanIdMappingIterator.hasNext() ) {
			
			String[] humanIdMappingRow = humanIdMappingIterator.next();
			String uniprotAcc = humanIdMappingRow[ 0 ];
			String dbName = humanIdMappingRow[ 1 ];
			String dbId = humanIdMappingRow[ 2 ];
			if ( "Gene_Name".equals( dbName ) ) {
				symbolUniProtMap.put( dbId.toLowerCase(), uniprotAcc );
			}
			
		}
		
		/**
		 * Processing BIOM_LOCUS_MASTER.csv file to collect LOCUS_ID, gene symbol information
		 */
		Iterator<String[]> locusMasterIterator = getLocusMasterIterator();
		locusMasterIterator.next(); // Skip header
		while( locusMasterIterator.hasNext() ) {
			
			String[] locusMasterRow = locusMasterIterator.next();
			String locusId = locusMasterRow[ 0 ];
			String officialName = locusMasterRow[ 1 ];
			if( !"".equals( locusId ) && !"".equals( officialName ) ) {
				locusMap.put( locusId, officialName );
			}
			
		}
		
		/**
		 * Processing BIOM_STRUCTURE_MASTER.csv file to collect biomarker information
		 */
		int numberOfConversionFailure = 0;
		
		SmilesParser sp = new SmilesParser( DefaultChemObjectBuilder.getInstance() );
		InChIGeneratorFactory factory = InChIGeneratorFactory.getInstance();
		
		Iterator<String[]> biomStructureMasterIterator = FormattedTextParser.parseCsvDelimitedReader( reader );
		biomStructureMasterIterator.next(); // Skip header
		
		while( biomStructureMasterIterator.hasNext() ) {
			
			String[] biomStructureDetailsRow = biomStructureMasterIterator.next();
			String smiles = biomStructureDetailsRow[ 2 ];
			String CasNo = biomStructureDetailsRow[ 5 ];
			String biomarkerName = biomStructureDetailsRow[ 6 ];
			String biomarkerType = biomStructureDetailsRow[ 7 ];
			String chemicalNature = biomStructureDetailsRow[ 8 ];
			String therapeuticClass = biomStructureDetailsRow[ 9 ];
			String diseaseName = biomStructureDetailsRow[ 10 ];
			String locusId = biomStructureDetailsRow[ 12 ];
			String status = biomStructureDetailsRow[ 18 ];
			String rsNumber = biomStructureDetailsRow[ 25 ];
			
			LOG.debug( "biomarkerName="+biomarkerName+", biomarkerType="+biomarkerType+", chemicalNature="+chemicalNature+", therapeuticClass="+therapeuticClass+", diseaseName="+diseaseName+", locusId="+locusId+", rsNumber"+rsNumber );
			
			if( ! biomarkerMap.containsKey( biomarkerName ) ) {
				
				Item item = createItem( "Biomarker" );
				item.setAttribute( "name", biomarkerName );
				item.setAttribute( "biomarkerType", biomarkerType );
				item.setAttribute( "chemicalNature", chemicalNature );
				item.setAttribute( "therapeuticClass", therapeuticClass );
				item.setAttribute( "status", status );
				
				/**
				 * According to chemical nature of this biomarker, substantial instance will be created below
				 */
				if( "Variation".equals( chemicalNature ) && ! "".equals( rsNumber ) ) {
					
					// Register "Variation" biomarker as a SNP instance only if it has rs number
					Item snpItem = createItem( "SNP" );
					snpItem.setAttribute( "identifier",  rsNumber);
					store( snpItem );
					item.setReference( "snp", snpItem );
					
				}else if( "Gene".equals( chemicalNature ) && ! "".equals( locusId ) && locusMap.containsKey( locusId ) ) {
					
					item.setReference( "gene", getGene( locusMap.get( locusId ) ) );
					
				}else if( "Protein".equals( chemicalNature )
						&& ! "".equals( locusId )
						&& locusMap.containsKey( locusId )
						&& symbolUniProtMap.containsKey( locusMap.get(locusId) ) ) {
					
					item.setReference( "protein", getProtein( symbolUniProtMap.get( locusMap.get(locusId) )  ) );
					
				}else if( "Scoring scale".equals( biomarkerType ) ) {
					
					Item scoringScaleItem = createItem( "ScoringScale" );
					scoringScaleItem.setAttribute( "name", biomarkerName );
					store( scoringScaleItem );
					item.setReference( "scoringScale", scoringScaleItem );
					
				}else if( "Chemical compound".equals( chemicalNature ) && ! "".equals(smiles)) {
					
					if( ! compoundMap.containsKey( biomarkerName ) ) {
						
						IAtomContainer mol = null;
						try {
							// if conversion from SMILES to InChIkey fails, skip this entry
							mol = sp.parseSmiles( smiles );
						}catch( InvalidSmilesException e ) {
							numberOfConversionFailure += 1;
							continue;
						}
						InChIGenerator generator = factory.getInChIGenerator( mol );
						
						if( generator.getReturnStatus() != INCHI_RET.OKAY ) {
							
							continue;
							
						}
						
						String inchikey = generator.getInchiKey();
						
						Item compoundGroupItem = getCompoundGroup( inchikey, biomarkerName );
						
						Item compoundItem = getCompound( biomarkerName, inchikey, CasNo );
						compoundGroupItem.addToCollection( "compounds", compoundItem );
						compoundMap.put( biomarkerName, compoundItem );
						
					}
					
					item.setReference( "compound", compoundMap.get( biomarkerName ) );
					
				}else {
					
					continue;
					
				}
				
				biomarkerMap.put( biomarkerName, item );
			}
			
				
			Item biomarkerItem = biomarkerMap.get( biomarkerName );
				
			/**
			 * Finding out UMLS CUI for this disease
			 */
			String cui = mrConsoMap.get( diseaseName.toLowerCase() );
			if ( null != cui ) {
				
				biomarkerItem.addToCollection( "diseaseTerms", getDiseaseTerm( cui, diseaseName ) );
					
			}
			
			LOG.info( "Number of conversion(SMILES->InChIkey) failures: "+numberOfConversionFailure );
				
		}
		
		/**
		 * Store all Biomarker items
		 */
		for( Item biomarkerItem : biomarkerMap.values() ) {
			
			store( biomarkerItem );
			
		}
		
		/**
		 * Store all CompoundGroup items
		 */
		for( Item compoundGroup : compoundGroupMap.values() ) {
			
			store( compoundGroup );
			
		}
		
	}
	
	private Item getCompound( String name, String inchikey, String casRegistryNumber ) throws ObjectStoreException {
	
		Item compoundItem = compoundMap.get( name );
		if( null == compoundItem ) {
			
			Item item = createItem( "GobiomCompound" );
			item.setAttribute( "identifier", name );
			item.setAttribute( "name", name );
			item.setAttribute( "inchiKey", inchikey );
			item.setReference( "compoundGroup", compoundGroupMap.get( inchikey ) );
			if ( ! "".equals( casRegistryNumber ) )
				item.setAttribute( "casRegistryNumber", casRegistryNumber );
			store( item );
			compoundMap.put( name, item );
			
		}
		return compoundMap.get( name );
		
	}
		
	private Item getCompoundGroup( String inchikey, String name ) throws ObjectStoreException {
		
		Item compoundGroupItem = compoundGroupMap.get( inchikey );
		if ( null == compoundGroupItem ){
			
			Item item = createItem("CompoundGroup");
			item.setAttribute( "identifier", inchikey );
			item.setAttribute( "name", name );
			compoundGroupMap.put( inchikey, item );
			// Store items afterward
			
		}
		return compoundGroupMap.get( inchikey );
		
	}
	
	private String getDiseaseTerm( String cui, String diseaseName ) throws ObjectStoreException {
		
		String diseaseTermRef = diseaseTermMap.get( cui );
		if ( diseaseTermRef == null ){
			
			Item item = createItem("DiseaseTerm");
			item.setAttribute( "identifier", cui );
			item.setAttribute( "name", diseaseName );
			item.setAttribute( "description", diseaseName );
			store(item);
			String ref = item.getIdentifier();
			diseaseTermMap.put( cui, ref );
		}
		return diseaseTermMap.get( cui );
		
	}
	
	private String getGene( String geneSymbol ) throws ObjectStoreException {
		
		String ret = geneMap.get( geneSymbol );
		if (ret == null) {
			Item item = createItem( "Gene" );
			item.setAttribute( "symbol", geneSymbol );
			item.setReference( "organism", getOrganism( HOMO_SAPIENS_TAXON_ID ) );
			store(item);
			geneMap.put( geneSymbol, item.getIdentifier() );
		}
		return geneMap.get( geneSymbol );
		
	}
	
	private String getProtein( String uniprotAcc ) throws ObjectStoreException {
		
		String ret = proteinMap.get( uniprotAcc );
		if (ret == null) {
			Item item = createItem( "Protein" );
			item.setAttribute( "primaryIdentifier", uniprotAcc );
			store(item);
			proteinMap.put( uniprotAcc, item.getIdentifier() );
		}
		return proteinMap.get( uniprotAcc );
		
	}

	public String getDataSetTitle(String taxonId) {
		return DATASET_TITLE;
	}
	
	private Iterator<String[]> getHumanIdMappingIterator() throws IOException {
		return FormattedTextParser.parseDelimitedReader( new FileReader( this.humanIdMappingFile ), '\t' );
	}
	
	public void setHumanIdMappingFile( File humanIdMappingFile ) {
		this.humanIdMappingFile = humanIdMappingFile;
	}
	
	private Iterator<String[]> getLocusMasterIterator() throws IOException {
		return FormattedTextParser.parseCsvDelimitedReader( new FileReader( this.locusMasterFile ) );
	}
	
	public void setLocusMasterFile(File locusMasterFile) {
		this.locusMasterFile = locusMasterFile;
	}

	private Iterator<String[]> getMrConsoIterator() throws IOException {
		return FormattedTextParser.parseDelimitedReader( new FileReader( this.mrConsoFile ), '|' );
	}
	
	public void setMrConsoFile( File mrConsoFile ) {
		this.mrConsoFile = mrConsoFile;
	}
	
}
