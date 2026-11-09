package org.sensorhub.impl.sensor.meshtastic.outputs;


import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;

import org.meshtastic.proto.MeshProtos;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.meshtastic.MeshtasticSensor;
import org.vast.data.TextEncodingImpl;
import org.vast.swe.helper.GeoPosHelper;

public class MyNodeInfoOutput extends AbstractMeshtasticOutput {

    protected static final String NAME = "myNodeInfo";

    DataComponent recordDescription;
    DataEncoding recordEncoding;

    public MyNodeInfoOutput(MeshtasticSensor parentSensor) {
        super(NAME, parentSensor);

        GeoPosHelper fac = new GeoPosHelper();

        recordDescription = fac.createRecord()
                .name(getName())
                .addField("samplingTime", fac.createTime().asSamplingTimeIsoUTC())
                .addField("nodeId", fac.createCount())
                .build();

        recordEncoding = new TextEncodingImpl();
    }

    @Override
    public void onMessage(MeshProtos.FromRadio msg) {
        MeshProtos.MyNodeInfo nodeInfo = msg.getMyInfo();
        int nodeId = nodeInfo.getMyNodeNum();

        DataBlock dataBlock = latestRecord == null ? recordDescription.createDataBlock() : latestRecord.renew();

        dataBlock.setDoubleValue(0, System.currentTimeMillis()/1000d);
        dataBlock.setIntValue(1, nodeId);

        eventHandler.publish(new DataEvent(System.currentTimeMillis(), this, dataBlock));
    }

    @Override
    public boolean canHandle(MeshProtos.FromRadio msg) {
        return msg.hasMyInfo();
    }

    @Override
    public DataComponent getRecordDescription() {
        return recordDescription;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {
        return recordEncoding;
    }
}
