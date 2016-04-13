/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2016 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.driver.flir;

import java.io.ByteArrayOutputStream;
import java.nio.ByteOrder;
import java.util.EnumSet;
import net.opengis.swe.v20.BinaryBlock;
import net.opengis.swe.v20.BinaryComponent;
import net.opengis.swe.v20.BinaryEncoding;
import net.opengis.swe.v20.ByteEncoding;
import net.opengis.swe.v20.DataArray;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataRecord;
import net.opengis.swe.v20.DataType;
import net.opengis.swe.v20.Time;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.api.sensor.SensorException;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.cdm.common.CDMException;
import org.vast.data.AbstractDataBlock;
import org.vast.data.DataBlockMixed;
import org.vast.data.SWEFactory;
import org.vast.swe.SWEConstants;
import org.vast.swe.SWEHelper;
import com.flir.flironesdk.Device;
import com.flir.flironesdk.Device.StreamDelegate;
import com.flir.flironesdk.Frame;
import com.flir.flironesdk.FrameProcessor;
import com.flir.flironesdk.RenderedImage;
import com.flir.flironesdk.RenderedImage.ImageType;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.view.SurfaceHolder;


/**
 * <p>
 * Implementation of data interface for FLIR One camera using FLIR SDK
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Mar 5, 2016
 */
public class FlirOneCameraOutput extends AbstractSensorOutput<FlirOneCameraDriver> implements StreamDelegate, FrameProcessor.Delegate
{
    // keep logger name short because in LogCat it's max 23 chars
    static final Logger log = LoggerFactory.getLogger(FlirOneCameraOutput.class.getSimpleName());
    protected static final String TIME_REF = "http://www.opengis.net/def/trs/BIPM/0/UTC";
    
    Device device;
    FrameProcessor frameProcessor;
    long samplingTime;
    
    int imgHeight, imgWidth, frameRate;
    ByteArrayOutputStream jpegBuf = new ByteArrayOutputStream();
    SurfaceHolder previewSurfaceHolder;
    
    String name;
    DataComponent dataStruct;
    BinaryEncoding dataEncoding;
    
    
    protected FlirOneCameraOutput(FlirOneCameraDriver parentModule, SurfaceHolder previewSurfaceHolder)
    {
        super(parentModule);
        this.name = "flirone_camera_data";
        this.previewSurfaceHolder = previewSurfaceHolder;
        
        imgWidth = 480;
        imgHeight = 640;
        frameRate = 9;
    }
    
    
    @Override
    public String getName()
    {
        return name;
    }
    
    
    @Override
    protected void init() throws SensorException
    {
        // create SWE Common data structure            
        SWEFactory fac = new SWEFactory();            
        dataStruct = fac.newDataRecord(2);
        dataStruct.setName(getName());
        
        Time time = fac.newTime();
        time.getUom().setHref(Time.ISO_TIME_UNIT);
        time.setDefinition(SWEConstants.DEF_SAMPLING_TIME);
        time.setReferenceFrame(TIME_REF);
        dataStruct.addComponent("time", time);
                
        DataArray img = fac.newDataArray(imgHeight);
        img.setDefinition("http://sensorml.com/ont/swe/property/VideoFrame");
        dataStruct.addComponent("videoFrame", img);
        
        DataArray imgRow = fac.newDataArray(imgWidth);
        img.addComponent("row", imgRow);
        
        DataRecord imgPixel = fac.newDataRecord(3);
        imgPixel.addComponent("red", fac.newCount(DataType.BYTE));
        imgPixel.addComponent("green", fac.newCount(DataType.BYTE));
        imgPixel.addComponent("blue", fac.newCount(DataType.BYTE));
        imgRow.addComponent("pixel", imgPixel);
        
        // SWE Common encoding
        dataEncoding = fac.newBinaryEncoding();
        dataEncoding.setByteEncoding(ByteEncoding.RAW);
        dataEncoding.setByteOrder(ByteOrder.BIG_ENDIAN);
        BinaryComponent timeEnc = fac.newBinaryComponent();
        timeEnc.setRef("/" + time.getName());
        timeEnc.setCdmDataType(DataType.DOUBLE);
        dataEncoding.addMemberAsComponent(timeEnc);
        //BinaryBlock compressedBlock = fac.newBinaryBlock();
        //compressedBlock.setRef("/" + img.getName());
        //compressedBlock.setCompression("H264");
        BinaryBlock compressedBlock = fac.newBinaryBlock();
        compressedBlock.setRef("/" + img.getName());
        compressedBlock.setCompression("JPEG");
        dataEncoding.addMemberAsBlock(compressedBlock);
        
        // resolve encoding so compressed blocks can be properly generated
        try
        {
            SWEHelper.assignBinaryEncoding(dataStruct, dataEncoding);
        }
        catch (CDMException e)
        {
            throw new RuntimeException("Cannot create camera output definition");
        }        
    }
    
    
    public void start(Context context, Device device)
    {
        this.device = device;
            
        // prepare frame processor
        frameProcessor = new FrameProcessor(context, this, EnumSet.of(ImageType.ThermalRGBA8888Image));
            
        // start video stream
        device.startFrameStream(this);
    }
    
    
    @Override
    public void onFrameReceived(Frame frame)
    {
        log.trace("Frame received");
        samplingTime = System.currentTimeMillis();
        frameProcessor.processFrame(frame);
    }
    
    
    @Override
    public void onFrameProcessed(RenderedImage img)
    {
        log.trace("Frame processed");
        
        // compress as JPEG
        jpegBuf.reset();
        Bitmap bitmap = img.getBitmap();
        bitmap.compress(CompressFormat.JPEG, 90, jpegBuf);
        
        // generate new data record
        DataBlock newRecord;
        if (latestRecord == null)
            newRecord = dataStruct.createDataBlock();
        else
            newRecord = latestRecord.renew();
        
        // set time stamp
        newRecord.setDoubleValue(0, samplingTime/1000.0);
        
        // set encoded data
        AbstractDataBlock frameData = ((DataBlockMixed)newRecord).getUnderlyingObject()[1];
        frameData.setUnderlyingObject(jpegBuf.toByteArray());
        
        // send event
        latestRecord = newRecord;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, FlirOneCameraOutput.this, latestRecord));          
    }
    
    
    @Override
    public void stop()
    {
        if (device != null)
        {
            device.stopFrameStream();
            device = null;
        }
    }


    @Override
    public double getAverageSamplingPeriod()
    {
        return 1./ (double)frameRate;
    }


    @Override
    public DataComponent getRecordDescription()
    {
        return dataStruct;
    }


    @Override
    public DataEncoding getRecommendedEncoding()
    {
        return dataEncoding;
    }

    
    @Override
    public DataBlock getLatestRecord()
    {
        return latestRecord;
    }
    
    
    @Override
    public long getLatestRecordTime()
    {
        return latestRecordTime;
    }
}
