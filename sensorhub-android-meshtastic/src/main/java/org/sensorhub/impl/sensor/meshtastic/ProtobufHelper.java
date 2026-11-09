package org.sensorhub.impl.sensor.meshtastic;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.DataArrayImpl;
import org.vast.swe.SWEBuilders;
import org.vast.swe.SWEHelper;

import java.util.List;
import java.util.Random;

public class ProtobufHelper {
    private static final SWEHelper sweHelper = new SWEHelper();
    private static final Random random = new Random();
    private static final Logger logger = LoggerFactory.getLogger(ProtobufHelper.class);

    private static final String COUNT_SUFFIX = "_count";
    private static final String ELEMENT_NAME = "element";
    private static final String ELEMENT_SUFFIX = "_element";
    private static final String TIMESTAMP_NAME = "timestamp";

    private ProtobufHelper() {
        // Private constructor to prevent instantiation
    }

    /**
     * Create a data block from a message.
     *
     * @param message The message to create a data block from
     * @return A data block containing the values from the message
     */
    public static SWEBuilders.DataRecordBuilder createDataRecord(Message message) {
        SWEBuilders.DataRecordBuilder dataRecordBuilder = sweHelper.createRecord();

        for (Descriptors.FieldDescriptor fieldDescriptor : message.getDescriptorForType().getFields()) {
            createDataRecordField(message, fieldDescriptor, dataRecordBuilder, null);
        }

        return dataRecordBuilder;
    }

    /**
     * Create a data block from a message with an added timestamp field.
     *
     * @param message The message to create a data block from
     * @return A data block containing the values from the message
     */
    public static SWEBuilders.DataRecordBuilder createDataRecordOutput(Message message) {
        SWEBuilders.DataRecordBuilder dataRecordBuilder = sweHelper.createRecord();

        dataRecordBuilder.addField(TIMESTAMP_NAME, sweHelper.createTime()
                .asSamplingTimeIsoUTC()
                .label("Precision Time Stamp")
                .description(TIMESTAMP_NAME)
                .definition(SWEHelper.getPropertyUri(TIMESTAMP_NAME)));

        for (Descriptors.FieldDescriptor fieldDescriptor : message.getDescriptorForType().getFields()) {
            createDataRecordField(message, fieldDescriptor, dataRecordBuilder, null);
        }

        return dataRecordBuilder;
    }

    /**
     * Add a field to a data block from a message.
     *
     * @param message         The message to create a data block from
     * @param fieldDescriptor The field to add
     * @param builder         The data record builder to add the field to
     * @param overrideName    The name to use for the field. If null, the name from the field descriptor will be used.
     */
    private static void createDataRecordField(Message message, Descriptors.FieldDescriptor fieldDescriptor, SWEBuilders.DataRecordBuilder builder, String overrideName) {
        String name = fieldDescriptor.getName();
        if (overrideName != null) {
            name = overrideName;
        }

        switch (fieldDescriptor.getType()) {
            case STRING:
            case BYTES:
                if (fieldDescriptor.isRepeated()) {
                    builder.addField(name + COUNT_SUFFIX, sweHelper.createCount()
                            .id(name + COUNT_SUFFIX)
                            .label(name + COUNT_SUFFIX)
                            .description(name + COUNT_SUFFIX)
                            .definition(SWEHelper.getPropertyUri(name + COUNT_SUFFIX)));
                    builder.addField(name, sweHelper.createArray()
                            .label(name)
                            .description(name)
                            .definition(SWEHelper.getPropertyUri(name))
                            .withVariableSize(name + COUNT_SUFFIX)
                            .withElement(ELEMENT_NAME, sweHelper.createText()
                                    .label(name + ELEMENT_SUFFIX)
                                    .description(name + ELEMENT_SUFFIX)
                                    .definition(SWEHelper.getPropertyUri(name + ELEMENT_SUFFIX))
                                    .build()));
                } else {
                    builder.addField(name, sweHelper.createText()
                            .label(name)
                            .description(name)
                            .definition(SWEHelper.getPropertyUri(name)));
                }
                break;
            case INT32:
            case INT64:
            case UINT32:
            case UINT64:
            case DOUBLE:
            case FLOAT:
                if (fieldDescriptor.isRepeated()) {
                    builder.addField(name + COUNT_SUFFIX, sweHelper.createCount()
                            .id(name + COUNT_SUFFIX)
                            .label(name + COUNT_SUFFIX)
                            .description(name + COUNT_SUFFIX)
                            .definition(SWEHelper.getPropertyUri(name + COUNT_SUFFIX)));
                    builder.addField(name, sweHelper.createArray()
                            .label(name)
                            .description(name)
                            .definition(SWEHelper.getPropertyUri(name))
                            .withVariableSize(name + COUNT_SUFFIX)
                            .withElement(ELEMENT_NAME, sweHelper.createQuantity()
                                    .label(name + ELEMENT_SUFFIX)
                                    .description(name + ELEMENT_SUFFIX)
                                    .definition(SWEHelper.getPropertyUri(name + ELEMENT_SUFFIX))
                                    .build()));
                } else {
                    builder.addField(name, sweHelper.createQuantity()
                            .label(name)
                            .description(name)
                            .definition(SWEHelper.getPropertyUri(name)));
                }
                break;
            case BOOL:
                if (fieldDescriptor.isRepeated()) {
                    builder.addField(name + COUNT_SUFFIX, sweHelper.createCount()
                            .id(name + COUNT_SUFFIX)
                            .label(name + COUNT_SUFFIX)
                            .description(name + COUNT_SUFFIX)
                            .definition(SWEHelper.getPropertyUri(name + COUNT_SUFFIX)));
                    builder.addField(name, sweHelper.createArray()
                            .label(name)
                            .description(name)
                            .definition(SWEHelper.getPropertyUri(name))
                            .withVariableSize(name + COUNT_SUFFIX)
                            .withElement(ELEMENT_NAME, sweHelper.createBoolean()
                                    .label(name + ELEMENT_SUFFIX)
                                    .description(name + ELEMENT_SUFFIX)
                                    .definition(SWEHelper.getPropertyUri(name + ELEMENT_SUFFIX))
                                    .build()));
                } else {
                    builder.addField(name, sweHelper.createBoolean()
                            .label(name)
                            .description(name)
                            .definition(SWEHelper.getPropertyUri(name)));
                }
                break;
            case ENUM:
                Descriptors.EnumDescriptor enumDescriptor = fieldDescriptor.getEnumType();

                if (fieldDescriptor.isRepeated()) {
                    builder.addField(name + COUNT_SUFFIX, sweHelper.createCount()
                            .id(name + COUNT_SUFFIX)
                            .label(name + COUNT_SUFFIX)
                            .description(name + COUNT_SUFFIX)
                            .definition(SWEHelper.getPropertyUri(name + COUNT_SUFFIX)));
                    builder.addField(name, sweHelper.createArray()
                            .label(name)
                            .description(name)
                            .definition(SWEHelper.getPropertyUri(name))
                            .withVariableSize(name + COUNT_SUFFIX)
                            .withElement(ELEMENT_NAME, sweHelper.createCategory()
                                    .label(name + ELEMENT_SUFFIX)
                                    .description(name + ELEMENT_SUFFIX)
                                    .definition(SWEHelper.getPropertyUri(name + ELEMENT_SUFFIX))
                                    .addAllowedValues(String.valueOf(enumDescriptor.getValues()))
                                    .build()));
                } else {
                    builder.addField(name, sweHelper.createCategory()
                            .label(name)
                            .description(name)
                            .definition(SWEHelper.getPropertyUri(name))
                            .addAllowedValues(String.valueOf(enumDescriptor.getValues()))
                            .build());
                }

                break;
            case MESSAGE:
                if (fieldDescriptor.isRepeated()) {
                    // Create a new message of the same type as the field
                    Descriptors.Descriptor descriptor = fieldDescriptor.getMessageType();
                    DynamicMessage.Builder newBuilder = DynamicMessage.newBuilder(descriptor);
                    Message newMessage = newBuilder.build();

                    builder.addField(name + COUNT_SUFFIX, sweHelper.createCount()
                            .id(name + COUNT_SUFFIX)
                            .label(name + COUNT_SUFFIX)
                            .description(name + COUNT_SUFFIX)
                            .definition(SWEHelper.getPropertyUri(name + COUNT_SUFFIX)));
                    builder.addField(name, sweHelper.createArray()
                            .label(name)
                            .description(name)
                            .definition(SWEHelper.getPropertyUri(name))
                            .withVariableSize(name + COUNT_SUFFIX)
                            .withElement(ELEMENT_NAME, createDataRecord(newMessage)
                                    .label(name + ELEMENT_SUFFIX)
                                    .description(name + ELEMENT_SUFFIX)
                                    .definition(SWEHelper.getPropertyUri(name + ELEMENT_SUFFIX))
                                    .build()));
                    break;
                }

                Message messageField = (Message) message.getField(fieldDescriptor);

                // Message type, but the message contains no fields. Skip it.
                if (messageField.getDescriptorForType().getFields().isEmpty()) {
                    break;
                }

                // Only one field in the message. Treat it as a single field to simplify the output.
                if (messageField.getDescriptorForType().getFields().size() == 1
                        && !messageField.getDescriptorForType().getFields().get(0).isRepeated()
                        && messageField.getDescriptorForType().getFields().get(0).getType() != Descriptors.FieldDescriptor.Type.MESSAGE) {
                    createDataRecordField(messageField, (messageField).getDescriptorForType().getFields().get(0), builder, name);
                    break;
                }

                // Recursive call to handle nested messages
                builder.addField(name, createDataRecord(messageField)
                        .label(name)
                        .description(name)
                        .definition(SWEHelper.getPropertyUri(name)));
                break;
            default:
                logger.info("{}: {} - This type is not supported yet.", name, fieldDescriptor.getType());
                break;
        }
    }

    /**
     * Set the values of a data block from a message.
     *
     * @param dataRecord The structure of the data
     * @param dataBlock  The data block to set the values in
     * @param message    The message to get the values from
     */
    public static void setDataBlock(DataRecord dataRecord, DataBlock dataBlock, Message message) {
        setDataBlock(dataRecord, dataBlock, message, 0);
    }

    /**
     * Set the values of a data block from a message.
     *
     * @param dataRecord The structure of the data
     * @param dataBlock  The data block to set the values in
     * @param message    The message to get the values from
     * @param timestamp  The current timestamp
     */
    public static void setDataBlockOutput(DataRecord dataRecord, DataBlock dataBlock, Message message, double timestamp) {
        int index = 0;
        dataBlock.setDoubleValue(index++, timestamp);

        setDataBlock(dataRecord, dataBlock, message, index);
    }

    /**
     * Set the values of a data block from a message. This version for recursion.
     *
     * @param dataRecord The structure of the data
     * @param dataBlock  The data block to set the values in
     * @param message    The message to get the values from
     * @param index      The index of the first field in the data block
     * @return The index of the next field in the data block
     */
    private static int setDataBlock(DataRecord dataRecord, DataBlock dataBlock, Message message, int index) {
        for (Descriptors.FieldDescriptor fieldDescriptor : message.getDescriptorForType().getFields()) {
            index = setDataBlockField(dataRecord, dataBlock, message, fieldDescriptor, index);
        }

        return index;
    }

    /**
     * Set a single field in a data block.
     *
     * @param dataBlock       The data block to set the field in
     * @param message         The message to get the field value from
     * @param fieldDescriptor The field to set
     * @param index           The index of the field in the data block
     * @return The index of the next field in the data block
     */
    private static int setDataBlockField(DataRecord dataRecord, DataBlock dataBlock, Message message, Descriptors.FieldDescriptor fieldDescriptor, int index) {
        switch (fieldDescriptor.getType()) {
            case STRING:
            case BYTES:
                if (fieldDescriptor.isRepeated()) {
                    @SuppressWarnings("unchecked")
                    java.util.List<String> array = (java.util.List<String>) message.getField(fieldDescriptor);
                    dataBlock.setIntValue(index++, array.size()); // Count field for the array

                    ((DataArrayImpl) dataRecord.getComponent(fieldDescriptor.getName())).updateSize();
                    dataBlock.updateAtomCount();

                    for (String value : array) {
                        dataBlock.setStringValue(index++, value);
                    }

                    return index;
                } else {
                    dataBlock.setStringValue(index, message.getField(fieldDescriptor).toString());
                    return ++index;
                }
            case INT32:
            case UINT32:
                if (fieldDescriptor.isRepeated()) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Integer> array = (java.util.List<Integer>) message.getField(fieldDescriptor);
                    dataBlock.setIntValue(index++, array.size()); // Count field for the array

                    ((DataArrayImpl) dataRecord.getComponent(fieldDescriptor.getName())).updateSize();
                    dataBlock.updateAtomCount();

                    for (Integer value : array) {
                        dataBlock.setIntValue(index++, value);
                    }

                    return index;
                } else {
                    dataBlock.setIntValue(index, (int) message.getField(fieldDescriptor));
                    return ++index;
                }
            case INT64:
            case UINT64:
                if (fieldDescriptor.isRepeated()) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Long> array = (java.util.List<Long>) message.getField(fieldDescriptor);
                    dataBlock.setIntValue(index++, array.size()); // Count field for the array

                    ((DataArrayImpl) dataRecord.getComponent(fieldDescriptor.getName())).updateSize();
                    dataBlock.updateAtomCount();

                    for (Long value : array) {
                        dataBlock.setLongValue(index++, value);
                    }

                    return index;
                } else {
                    dataBlock.setLongValue(index, (long) message.getField(fieldDescriptor));
                    return ++index;
                }
            case DOUBLE:
                if (fieldDescriptor.isRepeated()) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Double> array = (java.util.List<Double>) message.getField(fieldDescriptor);
                    dataBlock.setIntValue(index++, array.size()); // Count field for the array

                    ((DataArrayImpl) dataRecord.getComponent(fieldDescriptor.getName())).updateSize();
                    dataBlock.updateAtomCount();

                    for (Double value : array) {
                        dataBlock.setDoubleValue(index++, value);
                    }

                    return index;
                } else {
                    dataBlock.setDoubleValue(index, (double) message.getField(fieldDescriptor));
                    return ++index;
                }
            case FLOAT:
                if (fieldDescriptor.isRepeated()) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Float> array = (java.util.List<Float>) message.getField(fieldDescriptor);
                    dataBlock.setIntValue(index++, array.size()); // Count field for the array

                    ((DataArrayImpl) dataRecord.getComponent(fieldDescriptor.getName())).updateSize();
                    dataBlock.updateAtomCount();

                    for (Float value : array) {
                        dataBlock.setFloatValue(index++, value);
                    }

                    return index;
                } else {
                    dataBlock.setFloatValue(index, (float) message.getField(fieldDescriptor));
                    return ++index;
                }
            case BOOL:
                if (fieldDescriptor.isRepeated()) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Boolean> array = (java.util.List<Boolean>) message.getField(fieldDescriptor);
                    dataBlock.setIntValue(index++, array.size()); // Count field for the array

                    ((DataArrayImpl) dataRecord.getComponent(fieldDescriptor.getName())).updateSize();
                    dataBlock.updateAtomCount();

                    for (Boolean value : array) {
                        dataBlock.setBooleanValue(index++, value);
                    }

                    return index;
                } else {
                    dataBlock.setBooleanValue(index, (boolean) message.getField(fieldDescriptor));
                    return ++index;
                }
            case ENUM:
                if (fieldDescriptor.isRepeated()) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Descriptors.EnumValueDescriptor> array = (java.util.List<Descriptors.EnumValueDescriptor>) message.getField(fieldDescriptor);
                    dataBlock.setIntValue(index++, array.size()); // Count field for the array

                    ((DataArrayImpl) dataRecord.getComponent(fieldDescriptor.getName())).updateSize();
                    dataBlock.updateAtomCount();

                    for (Descriptors.EnumValueDescriptor value : array) {
                        dataBlock.setStringValue(index++, value.getName());
                    }

                    return index;
                } else {
                    dataBlock.setStringValue(index, ((Descriptors.EnumValueDescriptor) message.getField(fieldDescriptor)).getName());
                    return ++index;
                }
            case MESSAGE:
                if (fieldDescriptor.isRepeated()) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Message> messageArray = (java.util.List<Message>) message.getField(fieldDescriptor);
                    dataBlock.setIntValue(index++, messageArray.size()); // Count field for the array

                    ((DataArrayImpl) dataRecord.getComponent(fieldDescriptor.getName())).updateSize();
                    dataBlock.updateAtomCount();

                    DataArrayImpl dataArray = (DataArrayImpl) dataRecord.getComponent(fieldDescriptor.getName());

                    for (int i = 0; i < messageArray.size(); i++) {
                        DataRecord dataRecordChild = (DataRecord) dataArray.getComponent(i);
                        Message messageChild = messageArray.get(i);
                        index = setDataBlock(dataRecordChild, dataBlock, messageChild, index);
                    }
                    return index;
                }

                Message messageField = (Message) message.getField(fieldDescriptor);

                // Message type, but the message contains no fields. Skip it.
                if (messageField.getDescriptorForType().getFields().isEmpty()) {
                    return index;
                }

                // Only one field in the message. Treat it as a single field to simplify the output.
                if (messageField.getDescriptorForType().getFields().size() == 1
                        && !messageField.getDescriptorForType().getFields().get(0).isRepeated()
                        && messageField.getDescriptorForType().getFields().get(0).getType() != Descriptors.FieldDescriptor.Type.MESSAGE) {
                    return setDataBlockField(dataRecord, dataBlock, messageField, (messageField).getDescriptorForType().getFields().get(0), index);
                }

                // Recursive call to handle nested messages
                DataRecord dataRecordChild = (DataRecord) dataRecord.getComponent(fieldDescriptor.getName());
                return setDataBlock(dataRecordChild, dataBlock, messageField, index);
            default:
                logger.info("{}: {} - This type is not supported yet.", fieldDescriptor.getName(), fieldDescriptor.getType());
                return index;
        }
    }

    /**
     * Generate a message of the same type as the given message. The generated message will have random values for all fields.
     *
     * @param message The {@link Message} to generate a random message for. The message can be the default instance of the message. (getDefaultInstance)
     * @return A random message of the same type as the given message
     */
    public static Message generateMessage(Message message) {
        return generateMessage(message.newBuilderForType());
    }

    /**
     * Generate a message of the same type as the given message builder. The generated message will have random values for all fields.
     *
     * @param messageBuilder The {@link Message.Builder} to generate a random message for
     * @return A random message of the same type as the given message builder
     */
    public static Message generateMessage(Message.Builder messageBuilder) {
        for (Descriptors.FieldDescriptor fieldDescriptor : messageBuilder.getDescriptorForType().getFields()) {
            generateMessageField(messageBuilder, fieldDescriptor);
        }
        return messageBuilder.build();
    }

    /**
     * Generate a random value for a field in a message.
     *
     * @param messageBuilder  {@link Message.Builder} to generate a value for
     * @param fieldDescriptor The field to generate a value for
     */
    private static void generateMessageField(Message.Builder messageBuilder, Descriptors.FieldDescriptor fieldDescriptor) {
        switch (fieldDescriptor.getType()) {
            case STRING:
                if (fieldDescriptor.isRepeated()) {
                    int numFields = random.nextInt(5);
                    for (int i = 0; i < numFields; i++) {
                        messageBuilder.addRepeatedField(fieldDescriptor, "test" + random.nextInt());
                    }
                } else {
                    messageBuilder.setField(fieldDescriptor, "test" + random.nextInt());
                }
                break;
            case INT32:
            case INT64:
            case UINT32:
            case UINT64:
                if (fieldDescriptor.isRepeated()) {
                    int numFields = random.nextInt(5);
                    for (int i = 0; i < numFields; i++) {
                        messageBuilder.addRepeatedField(fieldDescriptor, random.nextInt());
                    }
                } else {
                    messageBuilder.setField(fieldDescriptor, random.nextInt());
                }
                break;
            case DOUBLE:
                if (fieldDescriptor.isRepeated()) {
                    int numFields = random.nextInt(5);
                    for (int i = 0; i < numFields; i++) {
                        messageBuilder.addRepeatedField(fieldDescriptor, random.nextDouble());
                    }
                } else {
                    messageBuilder.setField(fieldDescriptor, random.nextDouble());
                }
                break;
            case FLOAT:
                if (fieldDescriptor.isRepeated()) {
                    int numFields = random.nextInt(5);
                    for (int i = 0; i < numFields; i++) {
                        messageBuilder.addRepeatedField(fieldDescriptor, random.nextFloat());
                    }
                } else {
                    messageBuilder.setField(fieldDescriptor, random.nextFloat());
                }
                break;
            case BOOL:
                if (fieldDescriptor.isRepeated()) {
                    int numFields = random.nextInt(5);
                    for (int i = 0; i < numFields; i++) {
                        messageBuilder.addRepeatedField(fieldDescriptor, random.nextBoolean());
                    }
                } else {
                    messageBuilder.setField(fieldDescriptor, random.nextBoolean());
                }
                break;
            case MESSAGE:
                if (fieldDescriptor.isRepeated()) {
                    int numFields = random.nextInt(5);
                    for (int i = 0; i < numFields; i++) {
                        messageBuilder.addRepeatedField(fieldDescriptor, generateMessage(messageBuilder.newBuilderForField(fieldDescriptor)));
                    }
                } else {
                    messageBuilder.setField(fieldDescriptor, generateMessage(messageBuilder.newBuilderForField(fieldDescriptor)));
                }
                break;
            default:
                // Other types can use their default value
                break;
        }
    }
}