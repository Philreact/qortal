package test;

import org.junit.Test;

import qora.block.BlockChain;
import repository.DataException;

public class BlockchainTests extends Common {

	@Test
	public void testValidateOrRebuild() throws DataException {
		BlockChain.validate();
	}

}
