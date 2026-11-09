package org.sensorhub.impl.sensor.meshtastic.outputs;


import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;

import org.meshtastic.proto.MeshProtos;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.meshtastic.MeshtasticSensor;
import org.vast.data.TextEncodingImpl;
import org.vast.swe.helper.GeoPosHelper;

public class NodeInfoOutput extends AbstractMeshtasticOutput {

    protected static final String NAME = "nodeInfo";
    DataComponent recordDescription;
    DataEncoding recordEncoding;

    public NodeInfoOutput(MeshtasticSensor parentSensor) {
        super(NAME, parentSensor);

        GeoPosHelper fac = new GeoPosHelper();

        recordDescription = fac.createRecord()
                .name(getName())
                .addField("samplingTime", fac.createTime().asSamplingTimeIsoUTC())
                .addField("nodeId", fac.createCount())
                .addField("user", fac.createRecord()
                        .addField("id", fac.createText())
                        .addField("longName", fac.createText()))
                .addField("position", fac.createLocationVectorLLA())
                .addField("hopsAway", fac.createCount())
                .build();

        recordEncoding = new TextEncodingImpl();
    }

    @Override
    public void onMessage(MeshProtos.FromRadio msg) {
        MeshProtos.NodeInfo nodeInfo = msg.getNodeInfo();

        int nodeId = nodeInfo.getNum();
        String userId = nodeInfo.getUser().getId();
        String userLongName = nodeInfo.getUser().getLongName();
        MeshProtos.Position position = nodeInfo.getPosition();
        double lat = position.getLatitudeI() * 1e-7;
        double lon = position.getLongitudeI() * 1e-7;
        double alt = position.getAltitude();
        int hopsAway = nodeInfo.getHopsAway();

        DataBlock dataBlock = latestRecord == null ? recordDescription.createDataBlock() : latestRecord.renew();

        int i = 0;
        dataBlock.setDoubleValue(i++, System.currentTimeMillis()/1000d);
        dataBlock.setIntValue(i++, nodeId);
        dataBlock.setStringValue(i++, userId);
        dataBlock.setStringValue(i++, userLongName);
        dataBlock.setDoubleValue(i++, lat);
        dataBlock.setDoubleValue(i++, lon);
        dataBlock.setDoubleValue(i++, alt);
        dataBlock.setIntValue(i, hopsAway);

        eventHandler.publish(new DataEvent(System.currentTimeMillis(), this, dataBlock));
    }

    @Override
    public boolean canHandle(MeshProtos.FromRadio msg) {
        return msg.hasNodeInfo();
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
