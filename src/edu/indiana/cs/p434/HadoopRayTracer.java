	/*
 * 
 */
package edu.indiana.cs.p434;


import java.awt.Rectangle;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Scanner;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.hadoop.mapred.TextOutputFormat;

import threeD.raytracer.graphics.RGB;
import threeD.raytracer.util.Vector;
import edu.indiana.extreme.CameraSetup;
import edu.indiana.extreme.DistributedRayTracer;
import edu.indiana.extreme.SceneVectorGraphics;

/**
 * @author Gavin Whelan and Anthony Monaco
 *
 */
@SuppressWarnings("deprecation")
public class HadoopRayTracer {

	public static class Map extends MapReduceBase implements Mapper<LongWritable, Text, IntWritable, Text> {
		public void map(LongWritable key, Text value, OutputCollector<IntWritable, Text> output, Reporter reporter) throws IOException{
			String line = value.toString();	
			
			Scanner s = new Scanner(line);
			s.useDelimiter(" ");
			
			// id width height px py pz ax ay az sx sy sw sh url 
			
			int imageId = s.nextInt();
			int imageWidth = s.nextInt();
			int imageHeight = s.nextInt();
			double lx = s.nextDouble();
			double ly = s.nextDouble();
			double lz = s.nextDouble();
			double ax = s.nextDouble();
			double ay = s.nextDouble();
			double az = s.nextDouble();
			int sx = s.nextInt();
			int sy = s.nextInt();
			int sw = s.nextInt();
			int sh = s.nextInt();
			String url = s.next();
			/*
			 * Simply set up the camera location and direction
			 */
			
			CameraSetup cameraInformation = new CameraSetup(new Vector(
					lx, ly, lz), new Vector(ax, ay, az));

			/*
			 * Retrieve image height and width
			 */
						
			Rectangle subView = new Rectangle();
			subView.setBounds(sx, sy, sw, sh);
			RGB[][] imageRGB = null;

			/*
			 * Employ ray trace library to do the image ray tracing
			 */
			DistributedRayTracer tracer = new DistributedRayTracer();
			try{
				SceneVectorGraphics sceneHolder = new SceneVectorGraphics(new URL(url));
				imageRGB = tracer.rayTrace(cameraInformation, sceneHolder, imageWidth, imageHeight, subView);
			}
			catch(Exception e){
				if(true){
					throw new IOException("Fuckup");
				}
			}
			Text out = new Text();
		
			byte[] bytes = new byte[8];
	        for (int i = 0; i < 4; i++) {
	            int offset = (bytes.length - 1 - i) * 8;
	            bytes[i] = (byte)((imageWidth >>> offset) & 0xFF);
	        }
	        out.append(bytes, 0, 4);
			
			bytes = new byte[8];
	        for (int i = 0; i < 4; i++) {
	            int offset = (bytes.length - 1 - i) * 8;
	            bytes[i] = (byte)((imageHeight >>> offset) & 0xFF);
	        }
	        out.append(bytes, 0, 4);
	        
			bytes = new byte[imageHeight*imageWidth*3];
			
			for(int i = 0; i < imageHeight; i++){
				for(int j = 0; j < imageWidth; j++){
					bytes[(i*imageWidth+j)*3] = (byte)((int)(imageRGB[i][j].getRed() * 256));
					bytes[(i*imageWidth+j)*3+1] = (byte)((int)(imageRGB[i][j].getGreen() * 256));
					bytes[(i*imageWidth+j)*3+2] = (byte)((int)(imageRGB[i][j].getBlue() * 256));
				}
			
			}
			
			out.append(bytes, 0, imageHeight*imageWidth*3);
			output.collect(new IntWritable(imageId), out);
		}
	}
	
	public static class Reduce extends MapReduceBase implements Reducer<IntWritable, Text, IntWritable, Text> {
		public void reduce(IntWritable key, Iterator<Text> values, OutputCollector<IntWritable, Text> output, Reporter reporter) throws IOException{
			int width = 0;
			int height = 0;
			RGB[][] stitched = null;
			
			while(values.hasNext()){
				width = 0;
				height = 0;
				Text t = values.next();
				byte[] b = t.getBytes();
				// Getting Width, Height, and initializing image.
		        if(stitched == null){
					byte[] widthArray = Arrays.copyOfRange(b, 0, 4);
			        for (int i = 0; i < 4; i++) {
			            int offset = (3 - i) * 8;
			            width = width | (widthArray[i] << offset); 
			        }
			        byte[] heightArray = Arrays.copyOfRange(b, 4, 8);
			        for (int i = 0; i < 4; i++) {
			            int offset = (3 - i) * 8;
			            height = height | (heightArray[i] << offset); 
			        }
		        	stitched = new RGB[height][width];
		        	
		        }
		        
		        for(int i = 0; i < height; i++){
		        	for(int j = 0; j < width; j++){
		        		byte[] c = Arrays.copyOfRange(b, (i*width + j)*3+8, (i*width + j)*3+11);
		        		int red = (int)c[0];
		        		int green = (int)c[1];
		        		int blue = (int)c[2];
	        			stitched[i][j] = new RGB();
		        		if(!(red == 0 && green == 0 && blue == 0)){
		        			stitched[i][j].setRed(red/256.0);
		        			stitched[i][j].setGreen(green/256.0);
		        			stitched[i][j].setBlue(blue/256.0);
		        		}
		        	}
		        }
		        
			}
			
			Text out = new Text();
			
			byte[] bytes = new byte[4];
	        for (int i = 0; i < 4; i++) {
	            int offset = (bytes.length - 1 - i) * 8;
	            bytes[i] = (byte)((width >>> offset) & 0xFF);
	        }
	        out.append(bytes, 0, 4);
			
			bytes = new byte[4];
	        for (int i = 0; i < 4; i++) {
	            int offset = (bytes.length - 1 - i) * 8;
	            bytes[i] = (byte)((height >>> offset) & 0xFF);
	        }
	        out.append(bytes, 0, 4);
	        
			bytes = new byte[height*width*3];
			
			for(int i = 0; i < height; i++){
				for(int j = 0; j < width; j++){
					bytes[(i*width+j)*3] = (byte)((int)(stitched[i][j].getRed() * 256.0));
					bytes[(i*width+j)*3+1] = (byte)((int)(stitched[i][j].getGreen() * 256.0));
					bytes[(i*width+j)*3+2] = (byte)((int)(stitched[i][j].getBlue() * 256.0));
				}
			
			}
			
			out.append(bytes, 0, height*width*3);
			
			output.collect(key, out);
		
		}
	}
	
	
	public static void main(String[] args) throws Exception {
		JobConf conf = new JobConf(HadoopRayTracer.class);
		conf.setJobName("Raytracer");
		
		conf.setOutputKeyClass(IntWritable.class);
		conf.setOutputValueClass(Text.class);
		
		conf.setMapperClass(Map.class);
		conf.setCombinerClass(Reduce.class);
		conf.setReducerClass(Reduce.class);
		
		conf.setInputFormat(TextInputFormat.class);
		conf.setOutputFormat(TextOutputFormat.class);
				
		FileInputFormat.setInputPaths(conf, new Path(args[0]));
		FileOutputFormat.setOutputPath(conf, new Path(args[1]));
		
		JobClient.runJob(conf);
	}
}
