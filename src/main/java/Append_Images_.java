
import ij.*;
import ij.plugin.*;
import ij.gui.*;
import ij.process.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.reflect.Array;



public class Append_Images_ implements PlugIn 
{
  static String title = "Append Images";
  static final int validChannelMinCount = 1;
  static final int validChannelMaxCount = 8;
  static final String POSITIVE = "(+)";
  static final String NEGATIVE = "(-)";

  int stampCount = 0; /* Number of Image Stacks to import and analyze*/
  String[] imageTitles = null;
  ImagePlus[] inputImages = null; 
  ImagePlus outputImage;
  double[] channelThresholds = null;
  int comboOptions = 0;
  int[][] colocalizationCounts = null; //count by picture/stack
  int maxHeight = 0, sliceCount = 0, newWidth = 0, channelCount=0;
  int[] widths = null;
  int[] heights = null;
  String outputfilepath = "C:\\Users\\Isaac\\Desktop\\";
  String outputfilename = "";

  public void run(String arg) 
  {
    /* Check start conditions, don't know if necessary */
    if (IJ.versionLessThan("1.27w")) 
    {
      return;
    }

    /* How many image stacks will the user upload*/
    stampCount = returnStampCount();
    if (stampCount == 0)
    {
      return;
    }

    /* Set up arrays based on channels selected by user*/
    inputImages = new ImagePlus[stampCount];
    channelThresholds = new double[stampCount];
    widths = new int[stampCount];
    heights = new int[stampCount];

    /* Get Input Images, Check Sizes, Apply ROI Masks*/
    if (!getStampsPostRoiMask()) 
    {
      IJ.showMessage(title, "Error Getting Images");
      return; 
    }
    
    /* Hold 2^(#slices) for each image stamp*/
    if (sliceCount == 0)
    {
      IJ.showMessage(title, "Slice Count is Zero. Unknown Error. Exiting.");
      return;
    }
    
    for (int j = 0; j < stampCount; j++)
    {
      newWidth = newWidth + widths[j];
    }
    
    combineImages();
    IJ.showMessage(title, "Analysis Successful! Congrats! And best of luck saving the world :)");
  }
  
  /*
   * This function is sent the openWindowList and creates 
   * a list of strings to return using only image names.
   * */
  private String[] getImageOptions(int[] openWindowList)
  {
    String[] titles = new String[openWindowList.length];
    for (int i=0; i<openWindowList.length; i++) 
    {
      ImagePlus imp = WindowManager.getImage(openWindowList[i]);
      if (imp!=null) 
      {
        titles[i] = imp.getTitle();
      } else {
        titles[i] = "";
      }
    }
    return titles;
  }


  /* 
   * Return: # of Image Stamps to analyze, 0 on ERR or Exit
   * */
  private int returnStampCount()
  {
    double count = 0; 
    PrintWriter pw = null;

    while(pw == null) 
    {
      GenericDialog gd = new GenericDialog(title);
      gd.addMessage("This is the number of stack you would want to append.");
      gd.addNumericField("Image Stamp Count (whole number greater than zero):", 2, 0);
      gd.addStringField("Output Filepath", outputfilepath);
      gd.addStringField("Output Filename", outputfilename);
      gd.setCancelLabel("Exit");
      gd.showDialog();
      if (gd.wasCanceled()) 
      {
        return (int)0;
      }
      count = gd.getNextNumber();
      outputfilepath = gd.getNextString();
      outputfilename = gd.getNextString() + "_appeneded";
 
      if ((Double.isNaN(count)) || 
          (count < 0) ||
          (count != Math.ceil(count)))
      {
        IJ.showMessage(title, "ERR: Please enter a valid integer above zero:" + Integer.toString(validChannelMinCount) 
        + " and " + Integer.toString(validChannelMaxCount));
        return (int)0;
      }
      
      try 
      {
        pw = new PrintWriter(new File(outputfilepath + outputfilename));
      } 
      catch (FileNotFoundException e) 
      {
        IJ.showMessage(title, "ERR: Filepath does not exist. Please try again.");
        pw = null;
      }
    }
    pw.close();
    return (int)count;
  }

  /*
   * This will get the list of relevant channel names from the user
   * and validate they are the same size, before returning true or false
   * based on success or failure
   * Count: number of input images the user selected.
   * */
  
  private boolean getStampsPostRoiMask()
  {
    if (Array.getLength(inputImages) != stampCount)
    {
      IJ.showMessage(title, "ERR: Setup Failure. Image array length not equal to stampCount requested");
    }
    
    for (int i=0; i < stampCount; i++)
    {
      if(!getNextStamp(i))
      {
        IJ.showMessage(title, "ERR: Unknown Error Getting Input Image");
        return false;
      }
      if(!checkImageData(i))
      {
        IJ.showMessage(title, "ERR with Image Data, Exiting");
        return false;
      }
    }
    return true;
  }

  private boolean getNextStamp(int stampnumber)
  {
    /* Get the number of channels to analyze, and error check the input */
    int [] openWindowList = WindowManager.getIDList();
    if (openWindowList == null)
    {
      IJ.showMessage(title, "ERR: Please open images you would like to analyze.");
    }
    openWindowList = WindowManager.getIDList();
    if (openWindowList == null)
    {
      IJ.showMessage(title, "ERR: Unable to generate image options. Exiting.");
      return false; 
    }
    String [] imagetitleoptions = getImageOptions(openWindowList);
    GenericDialog gd = new GenericDialog(title);
    String option = "Channel: ";
    gd.addChoice(option, imagetitleoptions, imagetitleoptions[0]);
    gd.addNumericField("Channel Threshold Value (0-255):", 50, 1);
    gd.showDialog();
    if (gd.wasCanceled()) 
    {
      return false;
    }
    int imageIndex = gd.getNextChoiceIndex();
    channelThresholds[stampnumber] = gd.getNextNumber();
    inputImages[stampnumber] = WindowManager.getImage(openWindowList[imageIndex]);
    if (inputImages[stampnumber] == null)
    {
      IJ.showMessage(title, "ERR: Error loading Image.");
      return false; 
    }
   return true;
  }


  private boolean checkImageData(int i)
  {
    /*Check images for consistent sizing*/
    sliceCount = inputImages[i].getNSlices();
    heights[i] = inputImages[i].getHeight();
    widths[i] = inputImages[i].getWidth();
    channelCount = inputImages[i].getNChannels();
    if((sliceCount == 0) || (heights[i] == 0) || (widths[i] == 0))
    {
      String imagesize = "slices::" + Integer.toString(sliceCount) +
                           " height:" + Integer.toString(heights[i]) +
                           " width:"+ Integer.toString(widths[i]);
      IJ.showMessage(title, "ERR: Image must exist. " + imagesize);
      return false;
    }

    if(heights[i] > maxHeight)
    {
      maxHeight = heights[i];
    }
    return true;
  }


  private void combineImages()
  {
    ImageProcessor [] imageProcessor = new ImageProcessor[stampCount];
    int pixelOffset = 0;     
    ImagePlus outputImage = IJ.createHyperStack(outputfilename, newWidth, maxHeight, channelCount, sliceCount, inputImages[0].getNFrames(), inputImages[0].getBitDepth());
    ImageProcessor outputProcessor = outputImage.getProcessor();
    LUT[] lut = new LUT[channelCount];
    lut = inputImages[0].getLuts();

    for (int i=0; i<stampCount; i++)
    {
      imageProcessor[i] = inputImages[i].getProcessor();
    }
    

    for(int i=0; i<stampCount; i++)
    {
      for(int n=0; n<channelCount; n++)
      {
        inputImages[i].setC(n+1);
        outputImage.setC(n+1); 
        outputImage.setLut(lut[n]);
        for(int y=0; y<heights[i]; y++)
        {
          float row[] = new float[widths[i]];
          imageProcessor[i].getRow(0,y,row,widths[i]);
          outputProcessor.putRow(pixelOffset,y,row,widths[i]);
        }
        int rowZeros[] = new int[widths[i]];
        for (int y=heights[i]; y < maxHeight; y++)
        {
          outputProcessor.putRow(pixelOffset,y,rowZeros,widths[i]);
        }
      }
      pixelOffset = pixelOffset + widths[i];
    }
    outputImage.show();
    IJ.saveAs(outputImage, "tiff", outputfilepath);
  }

  
 
}

