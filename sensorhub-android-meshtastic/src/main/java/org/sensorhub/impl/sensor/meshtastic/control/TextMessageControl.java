package org.sensorhub.impl.sensor.meshtastic.control;

import com.google.protobuf.ByteString;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;

import org.meshtastic.proto.MeshProtos;
import org.meshtastic.proto.Portnums;
import org.sensorhub.api.command.CommandException;
import org.sensorhub.impl.sensor.AbstractSensorControl;
import org.sensorhub.impl.sensor.meshtastic.MeshtasticSensor;
import org.vast.swe.SWEHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TextMessageControl extends AbstractSensorControl<MeshtasticSensor> {

    public static final String NAME = "textMessage";
    protected final DataComponent commandDescription;

    public TextMessageControl(MeshtasticSensor parentSensor) {
        super(NAME, parentSensor);

        SWEHelper fac = new SWEHelper();

        commandDescription = fac.createRecord()
                .addField("message", fac.createText())
                .addField("destinationNodeId", fac.createCount())
                .build();
    }

    private MeshProtos.ToRadio createMessage(DataBlock cmdData) {
        String message = cmdData.getStringValue(0);
        int destinationNodeId = cmdData.getIntValue(1);

        MeshProtos.MeshPacket packet = MeshProtos.MeshPacket.newBuilder()
                .setDecoded(MeshProtos.Data.newBuilder()
                        .setPortnum(Portnums.PortNum.internalGetValueMap().findValueByNumber(Portnums.PortNum.TEXT_MESSAGE_APP_VALUE))
                        .setPayload(ByteString.copyFrom(message, StandardCharsets.UTF_8))
                        .build())
                .setTo(destinationNodeId)
                .setWantAck(false)
                .setFrom(0)
                .build();

        return MeshProtos.ToRadio.newBuilder()
                .setPacket(packet)
                .build();
    }

    @Override
    protected boolean execCommand(DataBlock cmdData) throws CommandException {
        MeshProtos.ToRadio msg = createMessage(cmdData);

        try {
            parentSensor.sendMessage(msg);
            return true;
        } catch (IOException e) {
            getLogger().error("Failed to send message", e);
            return false;
        }
    }

    @Override
    public DataComponent getCommandDescription() {
        return commandDescription;
    }

}
