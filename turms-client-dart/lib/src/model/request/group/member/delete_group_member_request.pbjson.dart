///
//  Generated code. Do not modify.
//  source: request/group/member/delete_group_member_request.proto
//
// @dart = 2.12
// ignore_for_file: annotate_overrides,camel_case_types,unnecessary_const,non_constant_identifier_names,library_prefixes,unused_import,unused_shown_name,return_of_invalid_type,unnecessary_this,prefer_final_fields,deprecated_member_use_from_same_package

import 'dart:core' as $core;
import 'dart:convert' as $convert;
import 'dart:typed_data' as $typed_data;
@$core.Deprecated('Use deleteGroupMemberRequestDescriptor instead')
const DeleteGroupMemberRequest$json = const {
  '1': 'DeleteGroupMemberRequest',
  '2': const [
    const {'1': 'group_id', '3': 1, '4': 1, '5': 3, '10': 'groupId'},
    const {'1': 'member_id', '3': 2, '4': 1, '5': 3, '10': 'memberId'},
    const {'1': 'successor_id', '3': 3, '4': 1, '5': 3, '9': 0, '10': 'successorId', '17': true},
    const {'1': 'quit_after_transfer', '3': 4, '4': 1, '5': 8, '9': 1, '10': 'quitAfterTransfer', '17': true},
  ],
  '8': const [
    const {'1': '_successor_id'},
    const {'1': '_quit_after_transfer'},
  ],
};

/// Descriptor for `DeleteGroupMemberRequest`. Decode as a `google.protobuf.DescriptorProto`.
final $typed_data.Uint8List deleteGroupMemberRequestDescriptor = $convert.base64Decode('ChhEZWxldGVHcm91cE1lbWJlclJlcXVlc3QSGQoIZ3JvdXBfaWQYASABKANSB2dyb3VwSWQSGwoJbWVtYmVyX2lkGAIgASgDUghtZW1iZXJJZBImCgxzdWNjZXNzb3JfaWQYAyABKANIAFILc3VjY2Vzc29ySWSIAQESMwoTcXVpdF9hZnRlcl90cmFuc2ZlchgEIAEoCEgBUhFxdWl0QWZ0ZXJUcmFuc2ZlcogBAUIPCg1fc3VjY2Vzc29yX2lkQhYKFF9xdWl0X2FmdGVyX3RyYW5zZmVy');
