// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: model/message/messages.proto

package im.turms.common.model.bo.message;

public final class MessagesOuterClass {
  private MessagesOuterClass() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_im_turms_proto_Messages_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_im_turms_proto_Messages_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\034model/message/messages.proto\022\016im.turms" +
      ".proto\032\033model/message/message.proto\"5\n\010M" +
      "essages\022)\n\010messages\030\001 \003(\0132\027.im.turms.pro" +
      "to.MessageB\'\n im.turms.common.model.bo.m" +
      "essageP\001\272\002\000b\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
          im.turms.common.model.bo.message.MessageOuterClass.getDescriptor(),
        });
    internal_static_im_turms_proto_Messages_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_im_turms_proto_Messages_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_im_turms_proto_Messages_descriptor,
        new java.lang.String[] { "Messages", });
    im.turms.common.model.bo.message.MessageOuterClass.getDescriptor();
  }

  // @@protoc_insertion_point(outer_class_scope)
}
