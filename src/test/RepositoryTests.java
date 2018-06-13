package test;

import static org.junit.Assert.*;

import org.junit.Test;

import repository.DataException;
import repository.Repository;
import repository.RepositoryManager;

public class RepositoryTests extends Common {

	@Test
	public void testGetRepository() throws DataException {
		Repository repository = RepositoryManager.getRepository();
		assertNotNull(repository);
	}

	@Test
	public void testMultipleInstances() throws DataException {
		int n_instances = 5;
		Repository[] repositories = new Repository[n_instances];

		for (int i = 0; i < n_instances; ++i) {
			repositories[i] = RepositoryManager.getRepository();
			assertNotNull(repositories[i]);
		}
	}

	@Test
	public void testAccessAfterCommit() throws DataException {
		Repository repository = RepositoryManager.getRepository();
		assertNotNull(repository);

		repository.saveChanges();

		try {
			repository.discardChanges();
			fail();
		} catch (DataException e) {
		}
	}

}
