package com.player2.playerengine.schematic;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static com.player2.playerengine.nativecommands.NativeAccess.*;

/** File-backed data import using the parser already bundled with PlayerEngine. */
public final class SchematicFiles {
    private static final int MAX_FILE=8*1024*1024,MAX_INFLATED=32*1024*1024;
    private SchematicFiles() {}
    public static Path resolve(Path directory,String name)throws IOException {
        if(name==null||name.length()>160||!name.matches("[A-Za-z0-9 _.-]+\\.(?:litematic|schem)"))
            throw new IOException("Use a .litematic or .schem filename in the game's schematics folder; convert legacy .schematic files first");
        Path root=directory.toRealPath(),file=root.resolve(name).toRealPath();
        if(!file.startsWith(root)||!Files.isRegularFile(file))throw new IOException("Schematic must be a regular file inside schematics");
        return file;
    }
    public static SchematicPlan read(Path directory,String filename)throws Exception {
        Path file=resolve(directory,filename);
        if(Files.size(file)>MAX_FILE)throw new IOException("Schematic exceeds 8 MiB compressed limit");
        byte[] bytes;try(var input=Files.newInputStream(file)){bytes=input.readNBytes(MAX_FILE+1);}
        if(bytes.length>MAX_FILE)throw new IOException("Schematic exceeds 8 MiB compressed limit");
        if(bytes.length<2)throw new IOException("Empty schematic file");
        // Bound expansion before the bundled NBT parser allocates arrays.
        byte[] plain=bytes;
        if((bytes[0]&255)==31&&(bytes[1]&255)==139)try(var input=new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            plain=input.readNBytes(MAX_INFLATED+1);
            if(plain.length>MAX_INFLATED)throw new IOException("Schematic exceeds 32 MiB expanded limit");
        }
        checkNbt(plain);
        Object schematic=callStatic("net.sandrohc.schematic4j.SchematicLoader","load",new ByteArrayInputStream(bytes));
        int[] dimensions=normalizedDimensions(schematic);
        int w=dimensions[0],h=dimensions[1],d=dimensions[2];
        long volume=(long)w*h*d;
        if(w<1||h<1||d<1||w>SchematicPlan.MAX_AXIS_XZ||d>SchematicPlan.MAX_AXIS_XZ||h>SchematicPlan.MAX_HEIGHT||volume>SchematicPlan.MAX_CELLS)
            throw new IOException("Schematic limit: 64x384x64, at most 1000000 cells");
        List<RegionData> regions=regionData(schematic);
        int[] blocks=new int[(int)volume];List<SchematicPlan.State> palette=new ArrayList<>();Map<SchematicPlan.State,Integer> indices=new HashMap<>();
        for(int y=0;y<h;y++)for(int z=0;z<d;z++)for(int x=0;x<w;x++) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Schematic import cancelled");
            SchematicPlan.State state;
            if(regions==null)state=decodeBlock(call(schematic,"block",x,y,z));
            else {state=new SchematicPlan.State("minecraft:air",Map.of());for(RegionData region:regions){var found=region.at(x,y,z);if(found!=null){state=found;break;}}}
            Integer index=indices.get(state);
            if(index==null){if(palette.size()>=4096)throw new IOException("Schematic palette exceeds 4096 distinct states");index=palette.size();indices.put(state,index);palette.add(state);}
            blocks[(y*d+z)*w+x]=index;
        }
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        return new SchematicPlan(filename,hash,w,h,d,palette,blocks);
    }
    private static SchematicPlan.State decodeBlock(Object block)throws Exception {
        @SuppressWarnings("unchecked") Map<String,String> properties=(Map<String,String>)call(block,"states");
        return new SchematicPlan.State(String.valueOf(call(block,"block")),properties);
    }
    private record RegionData(int x,int y,int z,int w,int h,int d,int[] blocks,List<SchematicPlan.State> palette){
        SchematicPlan.State at(int a,int b,int c)throws IOException {
            a-=x;b-=y;c-=z;if(a<0||b<0||c<0||a>=w||b>=h||c>=d)return null;
            int index=blocks[(b*d+c)*w+a];if(index<0||index>=palette.size())throw new IOException("Invalid region palette index");return palette.get(index);
        }
    }
    private static List<RegionData> regionData(Object schematic)throws Exception {
        if(!schematic.getClass().getSimpleName().equals("LitematicaSchematic"))return null;
        List<RegionData> result=new ArrayList<>();for(Object r:(Object[])get(schematic,"regions")){
            Object p=get(r,"position"),s=get(r,"size");List<SchematicPlan.State> palette=new ArrayList<>();
            Object[] original=(Object[])get(r,"blockStatePalette");if(original.length>4096)throw new IOException("Region palette exceeds budget");for(Object b:original)palette.add(decodeBlock(b));
            result.add(new RegionData((int)get(p,"x"),(int)get(p,"y"),(int)get(p,"z"),(int)get(s,"x"),(int)get(s,"y"),(int)get(s,"z"),(int[])get(r,"blockStates"),List.copyOf(palette)));
        }return List.copyOf(result);
    }
    /** schematic4j decodes negative-axis block arrays but leaves signed region bounds in block(). */
    private static int[] normalizedDimensions(Object schematic)throws Exception {
        if(!schematic.getClass().getSimpleName().equals("LitematicaSchematic"))return new int[]{((Number)call(schematic,"width")).intValue(),((Number)call(schematic,"height")).intValue(),((Number)call(schematic,"length")).intValue()};
        Object[] regions=(Object[])get(schematic,"regions");if(regions.length<1||regions.length>64)throw new IOException("Use 1 to 64 non-overlapping litematic regions");
        long[] min={Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE},max={Long.MIN_VALUE,Long.MIN_VALUE,Long.MIN_VALUE};List<int[]> boxes=new ArrayList<>();
        for(Object region:regions){
            Object start=get(region,"position"),size=get(region,"size");int[] box=new int[6];long volume=1;
            for(int axis=0;axis<3;axis++){
                String key=new String[]{"x","y","z"}[axis];int p=((Number)get(start,key)).intValue(),n=((Number)get(size,key)).intValue();long count=Math.abs((long)n);
                if(count<1||count>(axis==1?384:64))throw new IOException("Region exceeds schematic dimensions");
                long lo=Math.min((long)p,(long)p+n+(n<0?1:-1)),hi=lo+count;
                if(lo<Integer.MIN_VALUE||hi>Integer.MAX_VALUE)throw new IOException("Region position overflow");
                box[axis]=(int)lo;box[axis+3]=(int)count;min[axis]=Math.min(min[axis],lo);max[axis]=Math.max(max[axis],hi);volume*=count;
            }
            if(volume>1_000_000||((int[])get(region,"blockStates")).length!=volume)throw new IOException("Invalid region block array");
            for(int[] old:boxes)if(box[0]<old[0]+old[3]&&box[0]+box[3]>old[0]&&box[1]<old[1]+old[4]&&box[1]+box[4]>old[1]&&box[2]<old[2]+old[5]&&box[2]+box[5]>old[2])throw new IOException("Overlapping regions are ambiguous; export a single selection");
            boxes.add(box);
        }
        int[] dimensions=new int[3];for(int axis=0;axis<3;axis++){long n=max[axis]-min[axis];if(n<1||n>(axis==1?384:64))throw new IOException("Combined regions exceed schematic dimensions");dimensions[axis]=(int)n;}
        for(int i=0;i<regions.length;i++){
            int[] b=boxes.get(i);Class<?> vector=get(regions[i],"position").getClass();var constructor=vector.getConstructor(int.class,int.class,int.class);
            set(regions[i],"position",constructor.newInstance((int)(b[0]-min[0]),(int)(b[1]-min[1]),(int)(b[2]-min[2])));
            set(regions[i],"size",constructor.newInstance(b[3],b[4],b[5]));
        }
        return dimensions;
    }
    private static void checkNbt(byte[] bytes)throws IOException {
        var in=new DataInputStream(new ByteArrayInputStream(bytes));int type=in.readUnsignedByte();
        if(type!=10)throw new IOException("Schematic root must be an NBT compound");
        in.skipNBytes(in.readUnsignedShort());payload(in,type,0,new int[]{0,0},"");
        if(in.available()!=0)throw new IOException("Trailing schematic NBT data");
    }
    private static int length(DataInputStream in,int elementBytes)throws IOException {
        int n=in.readInt();if(n<0||(long)n*elementBytes>in.available())throw new IOException("Invalid schematic NBT array/list length");return n;
    }
    private static void dimension(String name,int value)throws IOException {
        int max=name.equals("Height")?384:(name.equals("Width")||name.equals("Length"))?64:0;
        if(max>0&&(value<1||value>max))throw new IOException("Schematic dimension exceeds limit: "+name);
    }
    private static void payload(DataInputStream in,int type,int depth,int[] tags,String name)throws IOException {
        if(depth>64||++tags[0]>2_000_000)throw new IOException("Schematic NBT nesting/tag budget exceeded");
        switch(type){
            case 1->in.skipNBytes(1);case 2->{int n=in.readShort();dimension(name,n);}case 3->{int n=in.readInt();dimension(name,n);}case 5->in.skipNBytes(4);case 4,6->in.skipNBytes(8);
            case 7->in.skipNBytes(length(in,1));case 8->in.skipNBytes(in.readUnsignedShort());
            case 9->{int child=in.readUnsignedByte(),n=length(in,1);if(child==0&&n!=0)throw new IOException("Invalid NBT end list");for(int i=0;i<n;i++)payload(in,child,depth+1,tags,"");}
            case 10->{
                int[] size=name.equals("Size")?new int[3]:null;
                while(true){int child=in.readUnsignedByte();if(child==0)break;String key=in.readUTF();
                    int axis=key.equals("x")?0:key.equals("y")?1:key.equals("z")?2:-1;
                    if(size!=null&&axis>=0){if(child!=3)throw new IOException("Invalid region size type");int n=in.readInt();long v=Math.abs((long)n);if(v<1||v>(axis==1?384:64))throw new IOException("Region dimension exceeds schematic limits");size[axis]=(int)v;}
                    else payload(in,child,depth+1,tags,key);
                }
                if(size!=null){long volume=(long)size[0]*size[1]*size[2];if(volume<1||volume>1_000_000||tags[1]+volume>1_000_000)throw new IOException("Region volume exceeds schematic budget");tags[1]+=(int)volume;}
            }
            case 11->in.skipNBytes((long)length(in,4)*4);case 12->in.skipNBytes((long)length(in,8)*8);
            default->throw new IOException("Invalid schematic NBT tag "+type);
        }
    }
}
