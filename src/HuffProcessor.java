import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;		//to see all debug spots search "if(myDebugLevel"
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){

		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
//		out.writeBits(BITS_PER_WORD+1,PSEUDO_EOF);	
		out.close();
	}
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while(true) {
			int oneChar = in.readBits(BITS_PER_WORD);
			if (oneChar == -1) {
//				out.writeBits(BITS_PER_WORD+1,PSEUDO_EOF);	
				break;
			}
			String code = codings[oneChar];
			out.writeBits(code.length(), Integer.parseInt(code,2));
		}	
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code,2));
	}
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if (root != null) {
			if(root.myLeft==null&&root.myRight==null){
				out.writeBits(1, 1);
				out.writeBits(BITS_PER_WORD + 1, root.myValue);
				if (myDebugLevel >= DEBUG_HIGH) {
					System.out.println("wrote leaf for tree "+root.myValue);
				}
			}
			else {
				out.writeBits(1, 0);
				writeHeader(root.myLeft,out);
				writeHeader(root.myRight,out);
			}
		}
	}	
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] codes = new String[ALPH_SIZE + 1];
		doTrails(codes,root,"");
		return codes;
	}
	private void doTrails(String[] codes, HuffNode t, String thusfar) {
		if (t != null) {
			if (t.myLeft==null&&t.myRight==null) {
				codes[t.myValue] = thusfar;
				if (myDebugLevel >= DEBUG_LOW) {
					System.out.printf("encoding for %d is %s%n",t.myValue,thusfar);
				}
			}
			else {
				doTrails(codes,t.myLeft,thusfar + "0");
				doTrails(codes,t.myRight,thusfar + "1");
			}
		}
	}
	private HuffNode makeTreeFromCounts(int[] freq) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		int freqct = 0;
		for (int i=0; i<freq.length; i++) {
			if (freq[i] != 0) {
				pq.add(new HuffNode(i,freq[i],null,null));
				freqct += 1;
				if (myDebugLevel >= DEBUG_HIGH) {
					System.out.printf("chunk %d occurs %d times%n", i, freq[i]);
				}
			}
		}
//		if (myDebugLevel >= DEBUG_LOW) {
//			System.out.println("counted chunk occurences "+freqct+" times");
//		}
		
		while(pq.size()>1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0,left.myWeight+right.myWeight,left,right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	private int[] readForCounts(BitInputStream input) {
		int[] freqs = new int[ALPH_SIZE + 1];
		while(true) {
			int bits = input.readBits(BITS_PER_WORD);
			if (bits == -1) break;
			String bitss = Integer.toString(bits);
			int integer = Integer.parseInt(bitss);
			freqs[integer] += 1;
		}
		freqs[ALPH_SIZE] = 1;
		if (myDebugLevel >= DEBUG_HIGH) System.out.println(freqs);
		return freqs;
	}
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out) {
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with " +bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}
	private void readCompressedBits(HuffNode root, BitInputStream input, BitOutputStream output) {
		HuffNode current = root;
		while(true) {
			int boi = input.readBits(1);
			if (boi == -1) throw new HuffException("%nbad input, no PSEDO_EOF");
			if (boi == 0) current = current.myLeft;
			if (boi == 1) current = current.myRight;
			
			if (current.myLeft==null&&current.myRight==null) {
				if (myDebugLevel >= DEBUG_HIGH) {
					System.out.print(current.myValue);
				}
				if (current.myValue == PSEUDO_EOF) break;
				else {
					output.writeBits(BITS_PER_WORD, current.myValue);
					current = root;
				}
			}
		}
	}
	private HuffNode readTreeHeader(BitInputStream in) {
		int oneBit = in.readBits(1);
		//iterate right and left when you encounter 0 (internal nodes)
		if (oneBit == -1) throw new HuffException("bad input, no PSEDO_EOF");
		if (oneBit == 0) {
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		//make leaf when you encounter 1 (leaf nodes)
		else if (oneBit == 1) {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value,0,null,null);
		}
		return null;
	}
}