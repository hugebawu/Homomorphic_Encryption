import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.List;

import security.DGK.DGKKeyPairGenerator;
import security.DGK.DGKOperations;
import security.DGK.DGKPrivateKey;
import security.DGK.DGKProvider;
import security.DGK.DGKPublicKey;
import security.DGK.DGKSignature;
import security.elgamal.ElGamalKeyPairGenerator;
import security.elgamal.ElGamalPrivateKey;
import security.elgamal.ElGamalProvider;
import security.elgamal.ElGamalCipher;
import security.elgamal.ElGamalPublicKey;
import security.elgamal.ElGamalSignature;
import security.elgamal.ElGamal_Ciphertext;
import security.generic.CipherConstants;
import security.generic.NTL;
import security.gm.GMCipher;
import security.gm.GMKeyPairGenerator;
import security.gm.GMPrivateKey;
import security.gm.GMPublicKey;
import security.paillier.PaillierCipher;
import security.paillier.PaillierKeyPairGenerator;
import security.paillier.PaillierPrivateKey;
import security.paillier.PaillierPublicKey;
import security.paillier.PaillierSignature;
import security.paillier.PaillierProvider;
import security.socialistmillionaire.alice;
import security.socialistmillionaire.bob;

public class Main 
{
	private static boolean isAlice = false;
	
	private static PaillierPublicKey pk;
	private static PaillierPrivateKey sk;
	
	private static DGKPublicKey pubKey;
	private static DGKPrivateKey privKey;
	
	private static ElGamalPublicKey e_pk;
	private static ElGamalPrivateKey e_sk;
	
	private static GMPublicKey gm_pk;
	private static GMPrivateKey gm_sk;
	
	// Initialize Alice and Bob
	private static ServerSocket bob_socket = null;
	private static Socket bob_client = null;
	private static bob andrew = null;
	private static alice Niu = null;

	// Get your test data...
	private static BigInteger [] low = generate_low();
	private static BigInteger [] mid = generate_mid();
	private static BigInteger [] high = generate_high();
	
	private static final int TEST = 100;    // Protocol 1-4 testing
	private static final int SIZE = 100000; // Stress-Test
	private static final int KEY_SIZE = 1024;
	private static final int BILLION = BigInteger.TEN.pow(9).intValue();
	
	public static void main(String [] args)
	{
		Security.addProvider(new DGKProvider());
		Security.addProvider(new PaillierProvider());
		Security.addProvider(new ElGamalProvider());

		if (args.length != 0)
		{
			System.out.println("Alice mode activated...");
			isAlice = true;
		}
		
		try
		{
			if (isAlice)
			{	
				Niu = new alice(new Socket("192.168.1.208", 9254));
				pk = Niu.getPaillierPublicKey();
				pubKey = Niu.getDGKPublicKey();
				e_pk = Niu.getElGamalPublicKey();
				
				// Line 99 in Alice matches to Line 158-165 in Bob
				// Lines 102-103 in Alice matches to Line 167-168 in Bob			
				// Lines 107-112 in Alices matches to Line 172 - 176 in Bob
				
				// Test K-min
				k_min();
				
				// Test Protocol 1 - 4 Functionality
				alice_demo();
				alice_demo_ElGamal();
			
				// Stress Test Protocol 1 - 4 Functionality
				Niu.setDGKMode(false);
				alice_Paillier();
				Niu.setDGKMode(true);
				alice_DGK();
				alice_ElGamal();
			}
			else
			{
				// Build DGK Keys
				DGKKeyPairGenerator gen = new DGKKeyPairGenerator(16, 160, 1024);
				gen.initialize(KEY_SIZE, null);
				KeyPair DGK = gen.generateKeyPair();
				pubKey = (DGKPublicKey) DGK.getPublic();
				privKey = (DGKPrivateKey) DGK.getPrivate();
				
				// Build Paillier Keys
				PaillierKeyPairGenerator p = new PaillierKeyPairGenerator();
				p.initialize(KEY_SIZE, null);
				KeyPair pe = p.generateKeyPair();
				pk = (PaillierPublicKey) pe.getPublic();
				sk = (PaillierPrivateKey) pe.getPrivate();
				
				// Build ElGamal Keys
				ElGamalKeyPairGenerator pg = new ElGamalKeyPairGenerator();
				// NULL -> ADDITIVE
				// NOT NULL -> MULTIPLICATIVE
				pg.initialize(KEY_SIZE, new SecureRandom());
				KeyPair el_gamal = pg.generateKeyPair();
				e_pk = (ElGamalPublicKey) el_gamal.getPublic();
				e_sk = (ElGamalPrivateKey) el_gamal.getPrivate();
				
				// Build GM Keys
				GMKeyPairGenerator gmg = new GMKeyPairGenerator();
				gmg.initialize(KEY_SIZE, null);
				KeyPair gm = gmg.generateKeyPair();
				gm_pk = (GMPublicKey) gm.getPublic();
				gm_sk = (GMPrivateKey) gm.getPrivate();
				
				// Stress Test
				// GM_Test();
				// Paillier_Test();
				// DGK_Test();
				// ElGamal_Test();
				
				bob_socket = new ServerSocket(9254);
				System.out.println("Bob is ready...");
				bob_client = bob_socket.accept();
				andrew = new bob(bob_client, pe, DGK, el_gamal);
				
				// Test K-Min using Protocol 4
				// Line 99 in Alice matches to Line 158-165 in Bob
				andrew.setDGKMode(false);
				andrew.run();// Sort Paillier
				andrew.setDGKMode(true);
				andrew.run();// Sort DGK
				if(e_pk.ADDITIVE)
				{
					andrew.repeat_ElGamal_Protocol4();
				}
				
				// Lines 102-103 in Alice matches to Line 167-168 in Bob
				bob_demo();
				bob_demo_ElGamal();
				
				// Stress Test the Protocols (get time to compute)
				// Lines 107-112 in Alices matches to Line 172 - 176 in Bob
				andrew.setDGKMode(false);
				bob(); //Paillier
				andrew.setDGKMode(true);
				bob(); //DGK
				bob_ElGamal();
			}
		}
		catch (IOException | ClassNotFoundException x)
		{
			x.printStackTrace();
		}
		catch(IllegalArgumentException o)
		{
			o.printStackTrace();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if(!isAlice)
			{
				try 
				{
					if(bob_client != null)
					{
						bob_client.close();
					}
					if(bob_socket != null)
					{
						bob_socket.close();
					}
				}
				catch (IOException e) 
				{
					e.printStackTrace();
				}
			}
		}
	}
	
	public static void k_min() 
			throws ClassNotFoundException, IllegalArgumentException, IOException
	{
		List<ElGamal_Ciphertext> t = new ArrayList<ElGamal_Ciphertext>();
		BigInteger [] toSort = new BigInteger[low.length];
		
		// Test Paillier Sorting
		Niu.setDGKMode(false);
		for(int i = 0; i < low.length;i++)
		{
			toSort[i] = NTL.generateXBitRandom(9);
			toSort[i] = PaillierCipher.encrypt(toSort[i], pk);
		}
		Niu.getKMin(toSort, 3);
		
		// Test DGK Sorting	
		Niu.setDGKMode(true);
		for(int i = 0; i < low.length;i++)
		{
			toSort[i] = NTL.generateXBitRandom(9);
			toSort[i] = DGKOperations.encrypt(pubKey, toSort[i]);
		}
		Niu.getKMin(toSort, 3);
		
		// Test ElGamal Sorting
		for(int i = 0; i < low.length;i++)
		{
			toSort[i] = NTL.generateXBitRandom(9);
			t.add(ElGamalCipher.encrypt(e_pk, toSort[i]));
		}
		if(e_pk.ADDITIVE)
		{
			Niu.getKMin_ElGamal(t, 3);
		}
	}
	
	// ------------------------------------ Basic demo methods-------------------------------------
	public static void alice_demo() throws ClassNotFoundException, IOException
	{	
		// Check the multiplication, DGK
		Niu.setDGKMode(true);
		System.out.println("Testing Multiplication with DGK");
		Niu.multiplication(DGKOperations.encrypt(pubKey, new BigInteger("100")), 
				DGKOperations.encrypt(pubKey, new BigInteger("2")));
		Niu.multiplication(DGKOperations.encrypt(pubKey, new BigInteger("1000")), 
				DGKOperations.encrypt(pubKey, new BigInteger("3")));
		Niu.multiplication(DGKOperations.encrypt(pubKey, new BigInteger("1000")), 
				DGKOperations.encrypt(pubKey, new BigInteger("5")));
		
		// Check the multiplication, Paillier
		Niu.setDGKMode(false);
		System.out.println("Testing Multiplication with Paillier");
		Niu.multiplication(PaillierCipher.encrypt(new BigInteger("100"), pk), 
				PaillierCipher.encrypt(new BigInteger("2"), pk));
		Niu.multiplication(PaillierCipher.encrypt(new BigInteger("1000"), pk), 
				PaillierCipher.encrypt(new BigInteger("3"), pk));
		Niu.multiplication(PaillierCipher.encrypt(new BigInteger("1000"), pk), 
				PaillierCipher.encrypt(new BigInteger("50"), pk));

		// Test Protocol 3, mode doesn't matter as DGK is always used!
		System.out.println("Protocol 3 Tests...");
		for(BigInteger l: low)
		{
			System.out.println(Niu.Protocol3(l));
		}
		for(BigInteger l: mid)
		{
			System.out.println(Niu.Protocol3(l));
		}
		for(BigInteger l: high)
		{
			System.out.println(!Niu.Protocol3(l));
		}
		for(BigInteger l: high)
		{
			System.out.println(!Niu.Protocol3(l));
		}
		for(BigInteger l: mid)
		{
			System.out.println(!Niu.Protocol3(l));
		}
		
		// Test Protocol 1
		for(BigInteger l: low)
		{
			System.out.println(Niu.Protocol1(l));
		}
		for(BigInteger l: mid)
		{
			System.out.println(Niu.Protocol1(l));
		}
		for(BigInteger l: high)
		{
			System.out.println(!Niu.Protocol1(l));
		}
		
		// Test Modified Protocol 3, mode doesn't matter as DGK is always used!
		System.out.println("Modified Protocol 3 Tests...");
		for(BigInteger l: low)
		{
			System.out.println(Niu.Modified_Protocol3(l));
		}
		for(BigInteger l: mid)
		{
			System.out.println(Niu.Modified_Protocol3(l));
		}
		for(BigInteger l: high)
		{
			System.out.println(!Niu.Modified_Protocol3(l));
		}
		
		// Test Protocol 2 (Builds on Protocol 3). REMEMEBER [X >= Y]
		// Paillier
		System.out.println("Protocol 2 Tests...Paillier");
		Niu.setDGKMode(false);
		for (int i = 0; i < low.length;i++)
		{
			System.out.println(!Niu.Protocol2(PaillierCipher.encrypt(low[i], pk), 
					PaillierCipher.encrypt(mid[i], pk)));
			System.out.println(Niu.Protocol2(PaillierCipher.encrypt(mid[i], pk), 
					PaillierCipher.encrypt(mid[i], pk)));
			System.out.println(Niu.Protocol2(PaillierCipher.encrypt(high[i], pk), 
					PaillierCipher.encrypt(mid[i], pk)));
		}
		
		// DGK
		System.out.println("Protocol 2 Tests...DGK...SKIPPED!");
		
		// Paillier, Protocol 4 returns (X >= Y)
		System.out.println("Protocol 4 Tests...Paillier");
		Niu.setDGKMode(false);
		for (int i = 0; i < low.length;i++)
		{
			System.out.println(!Niu.Protocol4(PaillierCipher.encrypt(low[i], pk), 
					PaillierCipher.encrypt(mid[i], pk)));
			System.out.println(Niu.Protocol4(PaillierCipher.encrypt(mid[i], pk), 
					PaillierCipher.encrypt(mid[i], pk)));
			System.out.println(Niu.Protocol4(PaillierCipher.encrypt(high[i], pk), 
					PaillierCipher.encrypt(mid[i], pk)));
		}
		
		// DGK, Protocol 4 returns (X > Y)
		Niu.setDGKMode(true);
		System.out.println("Protocol 4 Tests...DGK");
		for (int i = 0; i < low.length;i++)
		{
			System.out.println(!Niu.Protocol4(DGKOperations.encrypt(pubKey, low[i]), 
					DGKOperations.encrypt(pubKey, mid[i])));
			System.out.println(!Niu.Protocol4(DGKOperations.encrypt(pubKey, mid[i]), 
					DGKOperations.encrypt(pubKey, mid[i])));
			System.out.println(Niu.Protocol4(DGKOperations.encrypt(pubKey, high[i]), 
					DGKOperations.encrypt(pubKey, mid[i])));
		}
		
		// Division Test, Paillier
		// REMEMBER THE OUTPUT IS THE ENCRYPTED ANSWER, ONLY BOB CAN VERIFY THE ANSWER
		Niu.setDGKMode(false);
		System.out.println("Division Tests...Paillier");
		BigInteger D = PaillierCipher.encrypt(100, pk);
		BigInteger d = DGKOperations.encrypt(pubKey, 100);
		
		Niu.division(D, 2);//100/2 = 50
		Niu.division(D, 3);//100/3 = 33
		Niu.division(D, 4);//100/4 = 25
		Niu.division(D, 5);//100/5 = 20
		Niu.division(D, 25);//100/25 = 4

		Niu.setDGKMode(true);
		System.out.println("Division Tests...DGK");
		Niu.division(d, 2);//100/2 = 50
		Niu.division(d, 3);//100/3 = 33
		Niu.division(d, 4);//100/4 = 25
		Niu.division(d, 5);//100/5 = 20
		Niu.division(d, 25);//100/25 = 4
	}
	
	public static void bob_demo() throws ClassNotFoundException, IOException
	{
		// Test out-source multiplication, DGK
		andrew.setDGKMode(true);
		for(int i = 0; i < 3; i++)
		{
			andrew.multiplication();
		}
		andrew.setDGKMode(false);
		for(int i = 0; i < 3; i++)
		{
			andrew.multiplication();
		}
		System.out.println("Finished Testing Multiplication");
		
		// Test Protocol 3
		for(int i = 0; i < mid.length * 3; i++)
		{
			andrew.Protocol3(mid[i % mid.length]);
		}
		for(int i = 0; i < mid.length * 2; i++)
		{
			andrew.Protocol3(low[i % mid.length]);
		}
		System.out.println("Finished Testing Protocol 3");

		// Test Protocol 1
		for(int i = 0; i < mid.length * 3; i++)
		{
			andrew.Protocol1(mid[i % mid.length]);
		}
		System.out.println("Finished Testing Protocol 1");

		// Test Modified Protocol 3
		for(int i = 0; i < mid.length * 3; i++)
		{
			andrew.Modified_Protocol3(mid[i % mid.length]);
		}
		System.out.println("Finished Testing Modified Protocol 3");
		
		// Test Protocol 2 with Paillier
		andrew.setDGKMode(false);
		for(int i = 0; i < mid.length * 3; i++)
		{
			andrew.Protocol2();
		}
		System.out.println("Finished Testing Protocol 2 w/ Paillier");
		
		// Test Protocol 2 with ElGamal
		System.out.println("Finished Testing Protocol 2 w/ ElGamal");
		
		
		// Test Protocol 4 with Paillier
		andrew.setDGKMode(false);
		for(int i = 0; i < mid.length * 3; i++)
		{
			andrew.Protocol4();
		}
		System.out.println("Finished Testing Protocol 4 w/ Paillier");
			
		// Test Protocol 4 with DGK
		andrew.setDGKMode(true);
		for(int i = 0; i < mid.length * 3; i++)
		{
			andrew.Protocol4();
		}
				
		System.out.println("Finished Testing Protocol 4 w/ DGK");
		// Division Protocol Test, Paillier
		andrew.setDGKMode(false);
		andrew.division(2);
		andrew.division(3);
		andrew.division(4);
		andrew.division(5);
		andrew.division(25);
		
		// Division Test, DGK
		andrew.setDGKMode(true);
		andrew.division(2);
		andrew.division(3);
		andrew.division(4);
		andrew.division(5);
		andrew.division(25);
	}
	
	//--------------------------Basic demo methods with ElGamal------------------------------------------	
	public static void alice_demo_ElGamal() throws ClassNotFoundException, IOException
	{
		if(!e_pk.ADDITIVE)
		{
			System.out.println("ElGamal Secure Addition/Subtraction");
			// Addition
			Niu.addition(ElGamalCipher.encrypt(e_pk, new BigInteger("100")), ElGamalCipher.encrypt(e_pk, new BigInteger("100")));
			Niu.addition(ElGamalCipher.encrypt(e_pk, new BigInteger("400")), ElGamalCipher.encrypt(e_pk, new BigInteger("400")));
			Niu.addition(ElGamalCipher.encrypt(e_pk, new BigInteger("1000")), ElGamalCipher.encrypt(e_pk, new BigInteger("1000")));
			// Subtract
			Niu.addition(ElGamalCipher.encrypt(e_pk, new BigInteger("100")), ElGamalCipher.encrypt(e_pk, new BigInteger("100")));
			Niu.addition(ElGamalCipher.encrypt(e_pk, new BigInteger("400")), ElGamalCipher.encrypt(e_pk, new BigInteger("100")));
			Niu.addition(ElGamalCipher.encrypt(e_pk, new BigInteger("1000")), ElGamalCipher.encrypt(e_pk, new BigInteger("100")));
			return;
		}
		System.out.println("Multiplication Tests...ElGamal");
		// Check the multiplication, ElGamal
		Niu.multiplication(ElGamalCipher.encrypt(e_pk, new BigInteger("100")), 
				ElGamalCipher.encrypt(e_pk, new BigInteger("2")));
		Niu.multiplication(ElGamalCipher.encrypt(e_pk, new BigInteger("1000")), 
				ElGamalCipher.encrypt(e_pk, new BigInteger("3")));
		Niu.multiplication(ElGamalCipher.encrypt(e_pk, new BigInteger("1000")), 
				ElGamalCipher.encrypt(e_pk, new BigInteger("50")));

		System.out.println("Division Tests...ElGamal");
		Niu.division(ElGamalCipher.encrypt(e_pk, 100), 2);//100/2 = 50
		Niu.division(ElGamalCipher.encrypt(e_pk, 100), 3);//100/3 = 33
		Niu.division(ElGamalCipher.encrypt(e_pk, 100), 4);//100/4 = 25
		Niu.division(ElGamalCipher.encrypt(e_pk, 100), 5);//100/5 = 20
		Niu.division(ElGamalCipher.encrypt(e_pk, 100), 25);//100/25 = 4

		// ElGamal
		System.out.println("Protocol 4 Tests...ElGamal");
		for (int i = 0; i < low.length;i++)
		{
			System.out.println(!Niu.Protocol4(ElGamalCipher.encrypt(e_pk, low[i]), 
					ElGamalCipher.encrypt(e_pk, mid[i])));
			System.out.println(Niu.Protocol4(ElGamalCipher.encrypt(e_pk, mid[i]), 
					ElGamalCipher.encrypt(e_pk, mid[i])));
			System.out.println(Niu.Protocol4(ElGamalCipher.encrypt(e_pk, high[i]), 
					ElGamalCipher.encrypt(e_pk, mid[i])));
		}
	}

	public static void bob_demo_ElGamal() throws ClassNotFoundException, IOException
	{
		if(!e_pk.ADDITIVE)
		{
			// Addition
			andrew.addition(true);
			andrew.addition(true);
			andrew.addition(true);
			// Subtract
			andrew.addition(false);
			andrew.addition(false);
			andrew.addition(false);
			return;
		}

		for(int i = 0; i < 3; i++)
		{
			andrew.ElGamal_multiplication();
		}

		// Division Test, ElGamal	
		andrew.ElGamal_division(2);
		andrew.ElGamal_division(3);
		andrew.ElGamal_division(4);
		andrew.ElGamal_division(5);
		andrew.ElGamal_division(25);

		// Test Protocol 4 with ElGamal
		for(int i = 0; i < mid.length * 3; i++)
		{
			andrew.ElGamal_Protocol4();
		}
	}
	
	// -------------------------------show basic functionality of Protocol 1 - 4  with DGK and Paillier-------------------------
	
	public static void alice_Paillier() 
			throws ClassNotFoundException, IOException, IllegalArgumentException
	{
		System.out.println("Start Paillier Test");
		Niu.setDGKMode(false);
		long start;
		BigInteger x = NTL.generateXBitRandom(15);
		BigInteger y = NTL.generateXBitRandom(15);
		BigInteger a = NTL.generateXBitRandom(15);
		System.out.println("x: " + x);
		System.out.println("y: " + y);
		System.out.println("a: " + a);
		System.out.println("N: " + pk.getN());
		x = PaillierCipher.encrypt(x, pk);
		y = PaillierCipher.encrypt(y, pk);
		
		// MULTIPLICATION
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.multiplication(x, y);
		}
		System.out.println("Multiplication, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		
		// DIVISION
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.division(x, 1000);
		}
		System.out.println("Division, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		
		// PROTOCOL 1
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.Protocol1(a);
		}
		System.out.println("Protocol 1, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		
		// PROTOCOL 2
		if(!Niu.isDGK())
		{
			start = System.nanoTime();
			for(int i = 0; i < TEST; i++)
			{
				Niu.Protocol2(x, y);
			}
			System.out.println("Protocol 2, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");	
		}
		
		// PROTOCOL 3
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.Protocol3(a);
		}
		System.out.println("Protocol 3, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		
		// Modified Protocol 3
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.Modified_Protocol3(a);
		}
		System.out.println("Modified Protocol 3, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		
		// PROTOCOL 4
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.Protocol4(x, y);
		}
		System.out.println("Protocol 4, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
	}
	
	public static void alice_DGK()
			throws ClassNotFoundException, IOException, IllegalArgumentException
	{
		System.out.println("Start DGK Test");
		long start;
		Niu.setDGKMode(true);
		BigInteger x = NTL.generateXBitRandom(15);
		BigInteger y = NTL.generateXBitRandom(15);
		BigInteger a = NTL.generateXBitRandom(15);
		System.out.println("x: " + x);
		System.out.println("y: " + y);
		System.out.println("a: " + a);
		System.out.println("u: " + pubKey.getU());
		x = DGKOperations.encrypt(pubKey, x);
		y = DGKOperations.encrypt(pubKey, y);
		
		// MULTIPLICATION
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.multiplication(x, y);
		}
		System.out.println("Multiplication, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		
		// DIVISION
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.division(x, 1000);
		}
		System.out.println("Division, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		
		// PROTOCOL 1
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.Protocol1(a);
		}
		System.out.println("Protocol 1, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		
		// PROTOCOL 2
		if(!Niu.isDGK())
		{
			start = System.nanoTime();
			for(int i = 0; i < TEST; i++)
			{
				Niu.Protocol2(x, y);
			}
			System.out.println("Protocol 2, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		}
		else
		{
			System.out.println("Protocol 2, does not work for comparing two DGK encrypted values!");
		}
		
		// PROTOCOL 3
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.Protocol3(a);
		}
		System.out.println("Protocol 3, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.Modified_Protocol3(a);
		}
		System.out.println("Modified Protocol 3, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		
		// PROTOCOL 4
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.Protocol4(x, y);
		}
		System.out.println("Protocol 4, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
	}
	
	public static void bob() 
			throws ClassNotFoundException, IOException, IllegalArgumentException
	{
		BigInteger b = NTL.generateXBitRandom(15);
		System.out.println("b: " + b);
		// Test Code
		for(int i = 0; i < TEST; i++)
		{
			andrew.multiplication();
		}
		
		for(int i = 0; i < TEST; i++)
		{
			andrew.division(1000);
		}
		
		for(int i = 0; i < TEST; i++)
		{
			andrew.Protocol1(b);
		}
		
		if(!andrew.isDGK())
		{
			for(int i = 0; i < TEST; i++)
			{
				andrew.Protocol2();
			}
		}
		
		for(int i = 0; i < TEST; i++)
		{
			andrew.Protocol3(b);
		}
		
		for(int i = 0; i < TEST; i++)
		{
			andrew.Modified_Protocol3(b);
		}
		
		for(int i = 0; i < TEST; i++)
		{
			andrew.Protocol4();	
		}
	}
	
	// ------------------------------------ Stress Test Protocol 1 - 4 ElGamal-----------------------------------
	public static void alice_ElGamal() 
			throws ClassNotFoundException, IOException, IllegalArgumentException
	{
		System.out.println("Start ElGamal Test");
		long start;
		BigInteger _x = NTL.generateXBitRandom(15);
		BigInteger _y = NTL.generateXBitRandom(15);
		BigInteger a = NTL.generateXBitRandom(15);
		System.out.println("x : " + _x);
		System.out.println("y : " + _y);
		System.out.println("a : " + a);
		System.out.println("u : " + CipherConstants.FIELD_SIZE);
		ElGamal_Ciphertext x = ElGamalCipher.encrypt(e_pk, _x);
		ElGamal_Ciphertext y = ElGamalCipher.encrypt(e_pk, _y);
		
		if(!e_pk.ADDITIVE)
		{
			start = System.nanoTime();
			for(int i = 0; i < TEST; i++)
			{
				Niu.addition(x, y);
			}
			System.out.println("Addition, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
			return;
		}
		// MULTIPLICATION
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.multiplication(x, y);
		}
		System.out.println("Multiplication, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");

		// DIVISION
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.division(x, 1000);
		}
		System.out.println("Division, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		
		// PROTOCOL 1
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.Protocol1(a);
		}
		System.out.println("Protocol 1, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		
		// PROTOCOL 2
		System.out.println("Protocol 2, doesn't work for comparing two ElGamal encrypted values!");
		
		// PROTOCOL 3
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.Protocol3(a);
		}
		System.out.println("Protocol 3, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		
		// PROTOCOL 3
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.Modified_Protocol3(a);
		}
		System.out.println("Modified Protocol 3, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
		
		// PROTOCOL 4
		start = System.nanoTime();
		for(int i = 0; i < TEST; i++)
		{
			Niu.Protocol4(x, y);
		}
		System.out.println("Protocol 4, Time to complete " + TEST + " tests: " + (System.nanoTime() - start)/BILLION + " seconds");
	}
	
	public static void bob_ElGamal() 
			throws ClassNotFoundException, IOException
	{
		BigInteger b = NTL.generateXBitRandom(15);
		System.out.println("b: " + b);
		
		if(!e_pk.ADDITIVE)
		{
			for(int i = 0; i < TEST; i++)
			{
				andrew.addition(true);
			}
			return;
		}
		
		// Test Code
		
		for(int i = 0; i < TEST; i++)
		{
			andrew.ElGamal_multiplication();
		}
		
		for(int i = 0; i < TEST; i++)
		{
			andrew.ElGamal_division(1000);
		}
		
		for(int i = 0; i < TEST; i++)
		{
			andrew.Protocol1(b);
		}
		
		for(int i = 0; i < TEST; i++)
		{
			andrew.Protocol3(b);
		}
		
		for(int i = 0; i < TEST; i++)
		{
			andrew.Modified_Protocol3(b);
		}
		
		for(int i = 0; i < TEST; i++)
		{
			andrew.ElGamal_Protocol4();	
		}
	}
	
	// ------------------------------------ Generate numbers for Protocol 1-4 testing---------------------------
	//---------------------Generate numbers-----------------------------------
	public static BigInteger [] generate_low()
	{
		BigInteger [] test_set = new BigInteger[16];
		test_set[0] = new BigInteger("1");
		test_set[1] = new BigInteger("2");
		test_set[2] = new BigInteger("4");
		test_set[3] = new BigInteger("8");
		test_set[4] = new BigInteger("16");
		test_set[5] = new BigInteger("32");
		test_set[6] = new BigInteger("64");
		test_set[7] = new BigInteger("128");
		test_set[8] = new BigInteger("256");
		test_set[9] = new BigInteger("512");
		
		test_set[10] = new BigInteger("1024");
		test_set[11] = new BigInteger("2048");
		test_set[12] = new BigInteger("4096");
		test_set[13] = new BigInteger("8192");
		test_set[14] = new BigInteger("16384");
		test_set[15] = new BigInteger("32768");
		
		BigInteger t = BigInteger.ZERO;
		for (int i = 0; i < test_set.length;i++)
		{
			test_set[i] = test_set[i].add(t);
		}
		return test_set;
	}
	
	public static BigInteger[] generate_mid()
	{
		BigInteger [] test_set = new BigInteger[16];
		test_set[0] = new BigInteger("1");
		test_set[1] = new BigInteger("2");
		test_set[2] = new BigInteger("4");
		test_set[3] = new BigInteger("8");
		test_set[4] = new BigInteger("16");
		test_set[5] = new BigInteger("32");
		test_set[6] = new BigInteger("64");
		test_set[7] = new BigInteger("128");
		test_set[8] = new BigInteger("256");
		test_set[9] = new BigInteger("512");
		
		test_set[10] = new BigInteger("1024");
		test_set[11] = new BigInteger("2048");
		test_set[12] = new BigInteger("4096");
		test_set[13] = new BigInteger("8192");
		test_set[14] = new BigInteger("16384");
		test_set[15] = new BigInteger("32768");
		
		BigInteger t = new BigInteger("5");
		for (int i = 0; i < test_set.length; i++)
		{
			test_set[i] = test_set[i].add(t);
		}
		return test_set;
	}
	
	public static BigInteger[] generate_high()
	{
		BigInteger [] test_set = new BigInteger[16];
		
		test_set[0] = new BigInteger("1");
		test_set[1] = new BigInteger("2");
		test_set[2] = new BigInteger("4");
		test_set[3] = new BigInteger("8");
		test_set[4] = new BigInteger("16");
		test_set[5] = new BigInteger("32");
		test_set[6] = new BigInteger("64");
		test_set[7] = new BigInteger("128");
		test_set[8] = new BigInteger("256");
		test_set[9] = new BigInteger("512");
		
		test_set[10] = new BigInteger("1024");
		test_set[11] = new BigInteger("2048");
		test_set[12] = new BigInteger("4096");
		test_set[13] = new BigInteger("8192");
		test_set[14] = new BigInteger("16384");
		test_set[15] = new BigInteger("32768");
		
		BigInteger t = new BigInteger("10");
		for (int i = 0; i < test_set.length; i++)
		{
			test_set[i] = test_set[i].add(t);
		}
		return test_set;
	}
	
	// ----------------------------All Stress Test methods for Crypto-------------------------------------------------

	//------------------------------------- Stress test crypto methods------------------------------------------
	public static void Paillier_Test() throws InvalidKeyException, SignatureException
	{
		System.out.println("-----------PAILLIER TEST x" + SIZE + "--------------KEY: " + KEY_SIZE + "-----------");
		long start = 0;
		
		PaillierSignature sig = new PaillierSignature();
		sig.initSign(sk);
		sig.update(new BigInteger("42").toByteArray());
		byte [] cert = sig.sign();
		
		start = System.nanoTime();
		for(int i = 0; i < SIZE;i++)
		{
			sig.initVerify(pk);
			sig.update(BigInteger.valueOf(i).toByteArray());
			if(sig.verify(cert))
			{
				System.out.println("PAILLIER VALID AT: " + i);
			}
		}
		System.out.println("Time to complete signature: " + ((System.nanoTime() - start)/BILLION) + " seconds");
		
		BigInteger base = PaillierCipher.encrypt(NTL.generateXBitRandom(15), pk);
		BigInteger t = NTL.generateXBitRandom(15);
		
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			PaillierCipher.encrypt(t, pk);
		}
		System.out.println("Time to complete encryption: " + ((System.nanoTime() - start)/BILLION) + " seconds");
		
		t = PaillierCipher.encrypt(NTL.generateXBitRandom(15), pk);
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			PaillierCipher.decrypt(t, sk);	
		}
		System.out.println("Time to complete decryption: " + ((System.nanoTime() - start)/BILLION) + " seconds");
		
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			PaillierCipher.add(base, t, pk);
		}
		System.out.println("Time to complete addition: " + ((System.nanoTime() - start)/BILLION) + " seconds");

		t = NTL.generateXBitRandom(15);
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			PaillierCipher.multiply(base, t, pk);
		}
		System.out.println("Time to complete multiplication: " + ((System.nanoTime() - start)/BILLION) + " seconds");
	
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			PaillierCipher.add_plaintext(base, t, pk);
		}
		System.out.println("Time to complete addition (plaintext): " + ((System.nanoTime() - start)/BILLION) + " seconds");
	}
	
	
	public static void DGK_Test() throws InvalidKeyException, SignatureException
	{
		System.out.println("-----------DGK TEST x" + SIZE + "--------------KEY: " + KEY_SIZE + "-----------");
		BigInteger base = DGKOperations.encrypt(pubKey, NTL.generateXBitRandom(15));
		BigInteger t = NTL.generateXBitRandom(15);
		long start = 0;
		
		DGKSignature sig = new DGKSignature();
		sig.initSign(privKey);
		sig.update(new BigInteger("42").toByteArray());
		byte [] cert = sig.sign();

		start = System.nanoTime();
		for(int i = 0; i < SIZE;i++)
		{
			sig.initVerify(pubKey);
			sig.update(BigInteger.valueOf(i).toByteArray());
			if(sig.verify(cert))
			{
				System.out.println("DGK VALID AT: " + i);
			}
		}
		System.out.println("Time to complete signature: " + ((System.nanoTime() - start)/BILLION) + " seconds");
			
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			DGKOperations.encrypt(pubKey, t);
		}
		System.out.println("Time to complete encryption: " + ((System.nanoTime() - start)/BILLION) + " seconds");
	
		t = DGKOperations.encrypt(pubKey, t);
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			DGKOperations.decrypt(t, privKey);
		}
		System.out.println("Time to complete decryption: " + ((System.nanoTime() - start)/BILLION) + " seconds");
		
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			DGKOperations.add(pubKey, t, base);
		}
		System.out.println("Time to complete addition: " + ((System.nanoTime() - start)/BILLION) + " seconds");
	
		long exp =  NTL.generateXBitRandom(15).longValue();
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			DGKOperations.multiply(pubKey, base, exp);
		}
		System.out.println("Time to complete multiplication: " + ((System.nanoTime() - start)/BILLION) + " seconds");

		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			DGKOperations.add_plaintext(pubKey, base, exp);
		}
		System.out.println("Time to complete addition (plaintext): " + ((System.nanoTime() - start)/BILLION) + " seconds");
	}
	
	
	public static void ElGamal_Test() throws SignatureException, InvalidKeyException
	{
		System.out.println("-----------EL-GAMAL TEST x" + SIZE + "--------------KEY: " + KEY_SIZE + "-----------");

		ElGamal_Ciphertext base = ElGamalCipher.encrypt(e_pk, NTL.generateXBitRandom(15));
		BigInteger t = NTL.generateXBitRandom(15);
		ElGamal_Ciphertext temp = ElGamalCipher.encrypt(e_pk, t);
		
		long start = 0;
		
		ElGamalSignature sig = new ElGamalSignature();
		sig.initSign(e_sk);
		sig.update(new BigInteger("42").toByteArray());
		byte [] cert = sig.sign();

		start = System.nanoTime();
		for(int i = 0; i < SIZE;i++)
		{
			sig.initVerify(e_pk);
			sig.update(BigInteger.valueOf(i).toByteArray());
			if(sig.verify(cert))
			{
				System.out.println("ElGamal VALID AT: " + i);
			}
		}
		System.out.println("Time to complete signature: " + ((System.nanoTime() - start)/BILLION) + " seconds");
		
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{	
			ElGamalCipher.encrypt(e_pk, t);
		}
		System.out.println("Time to complete encryption: " + ((System.nanoTime() - start)/BILLION) + " seconds");
		
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			ElGamalCipher.decrypt(e_sk, temp);
		}
		System.out.println("Time to complete decryption: " + ((System.nanoTime() - start)/BILLION) + " seconds");
		
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			ElGamalCipher.add(temp, base, e_pk);
		}
		System.out.println("Time to complete addition: " + ((System.nanoTime() - start)/BILLION) + " seconds");
		
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			temp = ElGamalCipher.multiply_scalar(temp, t, e_pk);
		}
		System.out.println("Time to complete multiplication: " + ((System.nanoTime() - start)/BILLION) + " seconds");
	}
	
	
	public static void GM_Test()
	{
		System.out.println("-----------GM TEST x" + SIZE + "-----------------KEY: " + KEY_SIZE + "-----------");
		BigInteger t = NTL.generateXBitRandom(15);
		List<BigInteger> enc_t = GMCipher.encrypt(t, gm_pk);
		List<BigInteger> enc_z = GMCipher.encrypt(t, gm_pk);
		long start = 0;
		
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			GMCipher.encrypt(t, gm_pk);
		}
		System.out.println("Time to complete encryption: " + ((System.nanoTime() - start)/BILLION) + " seconds");

		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			GMCipher.decrypt(enc_t, gm_sk);
		}
		System.out.println("Time to complete decryption: " + ((System.nanoTime() - start)/BILLION) + " seconds");
		
		start = System.nanoTime();
		for(int i = 0; i < SIZE; i++)
		{
			GMCipher.xor(enc_t, enc_z, gm_pk);
		}
		System.out.println("Time to complete xor: " + ((System.nanoTime() - start)/BILLION) + " seconds");
	}
}
