package org.intermine.bio.postprocess;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreWriter;
import org.intermine.objectstore.intermine.ObjectStoreWriterInterMineImpl;
import org.intermine.postprocess.PostProcessor;
import org.intermine.sql.Database;

/**
 * For removing cas-registry-number in compound
 * 
 * @author chenyian
 *
 */
public class RemoveCompoundCasRegistryNumber extends PostProcessor {

	private static final Logger LOG = LogManager.getLogger(RemoveCompoundCasRegistryNumber.class);

	public RemoveCompoundCasRegistryNumber(ObjectStoreWriter osw) {
		super(osw);
	}

	public void removeCasNumberBySQL() {
		LOG.info("Start removing the cas-registry-number in compound.");
		if (osw instanceof ObjectStoreWriterInterMineImpl) {
			Database db = ((ObjectStoreWriterInterMineImpl) osw).getDatabase();
			Connection connection;
			try {
				connection = db.getConnection();

				Statement statement = connection.createStatement();

				statement.execute("UPDATE compound SET casregistrynumber=null");
//				statement.execute("UPDATE chebicompound SET casregistrynumber=null");
//				statement.execute("UPDATE chemblcompound SET casregistrynumber=null");
				statement.execute("UPDATE drugcompound SET casregistrynumber=null");
				statement.execute("UPDATE keggcompound SET casregistrynumber=null");
//				statement.execute("UPDATE pubchemcompound SET casregistrynumber=null");
//				statement.execute("UPDATE pdbcompound SET casregistrynumber=null");
				
//				connection.commit();
				statement.close();
				connection.close();

			} catch (SQLException e) {
				e.printStackTrace();
			}

		} else {
			throw new RuntimeException("the ObjectStoreWriter is not an " + "ObjectStoreWriterInterMineImpl");
		}
		LOG.info("Finish removing the cas-registry-number in compound.");
	}

	@Override
	public void postProcess() throws ObjectStoreException, IllegalAccessException {
		this.removeCasNumberBySQL();
	}
}
