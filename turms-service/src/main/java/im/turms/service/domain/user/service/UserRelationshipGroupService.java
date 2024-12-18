/*
 * Copyright (C) 2019 The Turms Project
 * https://github.com/turms-im/turms
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.turms.service.domain.user.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.ClientSession;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import im.turms.server.common.access.client.dto.ClientMessagePool;
import im.turms.server.common.access.client.dto.model.user.UserRelationshipGroupsWithVersion;
import im.turms.server.common.domain.common.service.BaseService;
import im.turms.server.common.infra.collection.CollectionUtil;
import im.turms.server.common.infra.collection.CollectorUtil;
import im.turms.server.common.infra.exception.ResponseException;
import im.turms.server.common.infra.exception.ResponseExceptionPublisherPool;
import im.turms.server.common.infra.logging.core.logger.Logger;
import im.turms.server.common.infra.logging.core.logger.LoggerFactory;
import im.turms.server.common.infra.random.RandomUtil;
import im.turms.server.common.infra.reactor.PublisherPool;
import im.turms.server.common.infra.time.DateRange;
import im.turms.server.common.infra.time.DateTimeUtil;
import im.turms.server.common.infra.validation.ValidUserRelationshipGroupKey;
import im.turms.server.common.infra.validation.ValidUserRelationshipKey;
import im.turms.server.common.infra.validation.Validator;
import im.turms.server.common.storage.mongo.IMongoCollectionInitializer;
import im.turms.server.common.storage.mongo.exception.DuplicateKeyException;
import im.turms.server.common.storage.mongo.operation.OperationResultConvertor;
import im.turms.service.domain.common.suggestion.UsesNonIndexedData;
import im.turms.service.domain.common.validation.DataValidator;
import im.turms.service.domain.user.po.UserRelationship;
import im.turms.service.domain.user.po.UserRelationshipGroup;
import im.turms.service.domain.user.po.UserRelationshipGroupMember;
import im.turms.service.domain.user.repository.UserRelationshipGroupMemberRepository;
import im.turms.service.domain.user.repository.UserRelationshipGroupRepository;
import im.turms.service.infra.proto.ProtoModelConvertor;
import im.turms.service.storage.mongo.OperationResultPublisherPool;

import static im.turms.server.common.domain.user.constant.UserConst.DEFAULT_RELATIONSHIP_GROUP_INDEX;

/**
 * @author James Chen
 */
@Service
@DependsOn(IMongoCollectionInitializer.BEAN_NAME)
public class UserRelationshipGroupService extends BaseService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(UserRelationshipGroupService.class);

    private final UserRelationshipGroupRepository userRelationshipGroupRepository;
    private final UserRelationshipGroupMemberRepository userRelationshipGroupMemberRepository;
    private final UserVersionService userVersionService;

    /**
     * @param userRelationshipService is lazy because: UserRelationshipService ->
     *                                UserRelationshipGroupService -> UserRelationshipService
     */
    public UserRelationshipGroupService(
            UserRelationshipGroupRepository userRelationshipGroupRepository,
            UserRelationshipGroupMemberRepository userRelationshipGroupMemberRepository,
            UserVersionService userVersionService,
            @Lazy UserRelationshipService userRelationshipService) {
        this.userRelationshipGroupRepository = userRelationshipGroupRepository;
        this.userRelationshipGroupMemberRepository = userRelationshipGroupMemberRepository;
        this.userVersionService = userVersionService;
    }

    public Mono<UserRelationshipGroup> createRelationshipGroup(
            @NotNull Long ownerId,
            @Nullable Integer groupIndex,
            @NotNull String groupName,
            @Nullable @PastOrPresent Date creationDate,
            @Nullable ClientSession session) {
        try {
            Validator.notNull(ownerId, "ownerId");
            Validator.notNull(groupName, "groupName");
            Validator.pastOrPresent(creationDate, "creationDate");
        } catch (ResponseException e) {
            return Mono.error(e);
        }
        Integer finalGroupIndex = groupIndex == null
                ? RandomUtil.nextPositiveInt()
                : groupIndex;
        if (creationDate == null) {
            creationDate = new Date();
        }
        UserRelationshipGroup group =
                new UserRelationshipGroup(ownerId, finalGroupIndex, groupName, creationDate);
        Mono<UserRelationshipGroup> result = userRelationshipGroupRepository.insert(group, session)
                .thenReturn(group);
        // If groupIndex is null, but the session isn't null and DuplicateKeyException occurs,
        // it is a bug of server because we cannot resume the session.
        // Luckily, we don't have the case now.
        if (groupIndex == null && session == null) {
            Date finalCreationDate = creationDate;
            return result.onErrorResume(DuplicateKeyException.class,
                    t -> createRelationshipGroup(ownerId,
                            null,
                            groupName,
                            finalCreationDate,
                            null));
        }
        return result;
    }

    public Flux<UserRelationshipGroup> queryRelationshipGroupsInfos(@NotNull Long ownerId) {
        try {
            Validator.notNull(ownerId, "ownerId");
        } catch (ResponseException e) {
            return Flux.error(e);
        }
        return userRelationshipGroupRepository.findRelationshipGroupsInfos(ownerId);
    }

    public Mono<UserRelationshipGroupsWithVersion> queryRelationshipGroupsInfosWithVersion(
            @NotNull Long ownerId,
            @Nullable Date lastUpdatedDate) {
        try {
            Validator.notNull(ownerId, "ownerId");
        } catch (ResponseException e) {
            return Mono.error(e);
        }
        return userVersionService.queryRelationshipGroupsLastUpdatedDate(ownerId)
                .flatMap(date -> {
                    if (DateTimeUtil.isAfterOrSame(lastUpdatedDate, date)) {
                        return ResponseExceptionPublisherPool.alreadyUpToUpdate();
                    }
                    UserRelationshipGroupsWithVersion.Builder builder =
                            ClientMessagePool.getUserRelationshipGroupsWithVersionBuilder()
                                    .setLastUpdatedDate(date.getTime());
                    return queryRelationshipGroupsInfos(ownerId)
                            .collect(CollectorUtil.toChunkedList())
                            .map(groups -> {
                                for (UserRelationshipGroup group : groups) {
                                    builder.addUserRelationshipGroups(
                                            ProtoModelConvertor.relationshipGroup2proto(group));
                                }
                                return builder.build();
                            });
                })
                .switchIfEmpty(ResponseExceptionPublisherPool.alreadyUpToUpdate());
    }

    @UsesNonIndexedData
    public Flux<Integer> queryGroupIndexes(@NotNull Long ownerId, @NotNull Long relatedUserId) {
        try {
            Validator.notNull(ownerId, "ownerId");
            Validator.notNull(relatedUserId, "relatedUserId");
        } catch (ResponseException e) {
            return Flux.error(e);
        }
        return userRelationshipGroupMemberRepository.findGroupIndexes(ownerId, relatedUserId);
    }

    public Flux<Long> queryRelationshipGroupMemberIds(
            @NotNull Long ownerId,
            @NotNull Integer groupIndex) {
        try {
            Validator.notNull(ownerId, "ownerId");
            Validator.notNull(groupIndex, "groupIndex");
        } catch (ResponseException e) {
            return Flux.error(e);
        }
        return userRelationshipGroupMemberRepository.findRelationshipGroupMemberIds(ownerId,
                groupIndex);
    }

    public Flux<Long> queryRelationshipGroupMemberIds(
            @Nullable Set<Long> ownerIds,
            @Nullable Set<Integer> groupIndexes,
            @Nullable Integer page,
            @Nullable Integer size) {
        return userRelationshipGroupMemberRepository
                .findRelationshipGroupMemberIds(ownerIds, groupIndexes, page, size);
    }

    public Mono<UpdateResult> updateRelationshipGroupName(
            @NotNull Long ownerId,
            @NotNull Integer groupIndex,
            @NotNull String newGroupName) {
        try {
            Validator.notNull(ownerId, "ownerId");
            Validator.notNull(groupIndex, "groupIndex");
            Validator.notNull(newGroupName, "newGroupName");
        } catch (ResponseException e) {
            return Mono.error(e);
        }
        return userRelationshipGroupRepository
                .updateRelationshipGroupName(ownerId, groupIndex, newGroupName)
                .flatMap(result -> userVersionService.updateRelationshipGroupsVersion(ownerId)
                        .onErrorResume(t -> {
                            LOGGER.error(
                                    "Caught an error while updating the relationship groups version of the owner ({}) after updating a relationship group name",
                                    ownerId,
                                    t);
                            return Mono.empty();
                        })
                        .thenReturn(result));
    }

    /**
     * @return the relationship index only if the related user is added to the group.
     */
    public Mono<Integer> upsertRelationshipGroupMember(
            @NotNull Long ownerId,
            @NotNull Long relatedUserId,
            @Nullable Integer newGroupIndex,
            @Nullable Integer deleteGroupIndex,
            @Nullable ClientSession session) {
        try {
            Validator.notNull(ownerId, "ownerId");
            Validator.notNull(relatedUserId, "relatedUserId");
        } catch (ResponseException e) {
            return Mono.error(e);
        }
        if (newGroupIndex != null) {
            if (deleteGroupIndex != null) {
                if (!newGroupIndex.equals(deleteGroupIndex)) {
                    return moveRelatedUserToNewGroup(ownerId,
                            relatedUserId,
                            deleteGroupIndex,
                            newGroupIndex,
                            false,
                            session).thenReturn(newGroupIndex);
                }
            } else {
                return addRelatedUserToRelationshipGroups(ownerId,
                        newGroupIndex,
                        relatedUserId,
                        session).flatMap(
                                addedNewRelatedUser -> addedNewRelatedUser
                                        ? Mono.just(newGroupIndex)
                                        : Mono.empty());
            }
        } else if (deleteGroupIndex != null
                && (!deleteGroupIndex.equals(DEFAULT_RELATIONSHIP_GROUP_INDEX))) {
            // TODO: Support specifying the behavior when a user removes a relationship from all
            // relationship groups (delete the relationship or not)
            // https://github.com/turms-im/turms/issues/1382
            return moveRelatedUserToNewGroup(ownerId,
                    relatedUserId,
                    deleteGroupIndex,
                    DEFAULT_RELATIONSHIP_GROUP_INDEX,
                    true,
                    session).thenReturn(DEFAULT_RELATIONSHIP_GROUP_INDEX);
        }
        return Mono.empty();
    }

    public Mono<UpdateResult> updateRelationshipGroups(
            @NotEmpty Set<UserRelationshipGroup.@ValidUserRelationshipGroupKey Key> keys,
            @Nullable String name,
            @Nullable @PastOrPresent Date creationDate) {
        try {
            Validator.notEmpty(keys, "keys");
            for (UserRelationshipGroup.Key key : keys) {
                DataValidator.validRelationshipGroupKey(key);
            }
            Validator.pastOrPresent(creationDate, "creationDate");
        } catch (ResponseException e) {
            return Mono.error(e);
        }
        if (name == null && creationDate == null) {
            return OperationResultPublisherPool.ACKNOWLEDGED_UPDATE_RESULT;
        }
        return userRelationshipGroupRepository.updateRelationshipGroups(keys, name, creationDate);
    }

    /**
     * @return true if added a new related user to the relationship group.
     * @implNote We call this method while upserting a relationship into the owner's relationship
     *           group in a transaction currently, so we don't check whether the owner has a
     *           relationship with the related user in this method.
     */
    public Mono<Boolean> addRelatedUserToRelationshipGroups(
            @NotNull Long ownerId,
            @NotNull Integer groupIndex,
            @NotNull Long relatedUserId,
            @Nullable ClientSession session) {
        try {
            Validator.notNull(groupIndex, "groupIndex");
        } catch (ResponseException e) {
            return Mono.error(e);
        }
        UserRelationshipGroupMember member =
                new UserRelationshipGroupMember(ownerId, groupIndex, relatedUserId, new Date());
        return userRelationshipGroupMemberRepository.upsert(member, session)
                .flatMap(updateResult -> {
                    boolean addedNewRelatedUser = updateResult.getUpsertedId() != null;
                    if (!addedNewRelatedUser && updateResult.getModifiedCount() <= 0) {
                        return PublisherPool.FALSE;
                    }
                    return userVersionService.updateRelationshipGroupsVersion(ownerId)
                            .onErrorResume(t -> {
                                LOGGER.error(
                                        "Caught an error while updating the relationship groups version of the owner ({}) after adding a user to the groups",
                                        ownerId,
                                        t);
                                return Mono.empty();
                            })
                            .thenReturn(addedNewRelatedUser);
                });
    }

    public Mono<Void> deleteRelationshipGroupAndMoveMembersToNewGroup(
            @NotNull Long ownerId,
            @NotNull Integer deleteGroupIndex,
            @NotNull Integer newGroupIndex) {
        try {
            Validator.notNull(ownerId, "ownerId");
            Validator.notNull(deleteGroupIndex, "deleteGroupIndex");
            Validator.notNull(newGroupIndex, "newGroupIndex");
            Validator.notEquals(deleteGroupIndex,
                    DEFAULT_RELATIONSHIP_GROUP_INDEX,
                    "The default relationship group cannot be deleted");
        } catch (ResponseException e) {
            return Mono.error(e);
        }
        if (deleteGroupIndex.equals(newGroupIndex)) {
            return Mono.empty();
        }
        // Don't use transaction for better performance
        return userRelationshipGroupMemberRepository
                .findRelationshipGroupMembers(ownerId, deleteGroupIndex)
                .collect(CollectorUtil.toChunkedList())
                .flatMap(members -> {
                    if (members.isEmpty()) {
                        return Mono.empty();
                    }
                    List<UserRelationshipGroupMember> newMembers = new ArrayList<>(members.size());
                    Date now = new Date();
                    for (UserRelationshipGroupMember member : members) {
                        UserRelationshipGroupMember.Key memberKey = member.getKey();
                        UserRelationshipGroupMember groupMember = new UserRelationshipGroupMember(
                                memberKey.getOwnerId(),
                                newGroupIndex,
                                memberKey.getRelatedUserId(),
                                now);
                        newMembers.add(groupMember);
                    }
                    return userRelationshipGroupMemberRepository.insertAllOfSameType(newMembers)
                            .onErrorComplete(DuplicateKeyException.class);
                })
                .then(userRelationshipGroupMemberRepository
                        .deleteAllRelatedUserFromRelationshipGroup(ownerId, deleteGroupIndex))
                .then(userRelationshipGroupRepository
                        .deleteById(new UserRelationshipGroup.Key(ownerId, deleteGroupIndex)))
                .then(userVersionService.updateRelationshipGroupsVersion(ownerId)
                        .onErrorResume(t -> {
                            LOGGER.error(
                                    "Caught an error while updating the relationship groups version of the owner ({}) after deleting relationships",
                                    ownerId,
                                    t);
                            return Mono.empty();
                        }))
                .then();
    }

    public Mono<DeleteResult> deleteAllRelationshipGroups(
            @NotEmpty Set<Long> ownerIds,
            @Nullable ClientSession session,
            boolean updateRelationshipGroupsVersion) {
        try {
            Validator.notEmpty(ownerIds, "ownerIds");
        } catch (ResponseException e) {
            return Mono.error(e);
        }
        Mono<DeleteResult> deleteMono =
                userRelationshipGroupRepository.deleteAllRelationshipGroups(ownerIds, session);
        if (updateRelationshipGroupsVersion) {
            return deleteMono
                    .flatMap(result -> userVersionService.updateRelationshipGroupsVersion(ownerIds)
                            .onErrorResume(t -> {
                                LOGGER.error(
                                        "Caught an error while updating the relationship groups version of the owners {} after deleting all groups",
                                        ownerIds,
                                        t);
                                return Mono.empty();
                            })
                            .thenReturn(result));
        }
        return deleteMono;
    }

    public Mono<DeleteResult> deleteRelatedUserFromRelationshipGroup(
            @NotNull Long ownerId,
            @NotNull Long relatedUserId,
            @NotNull Integer groupIndex,
            @Nullable ClientSession session,
            boolean updateRelationshipGroupsMembersVersion) {
        try {
            Validator.notNull(ownerId, "ownerId");
            Validator.notNull(relatedUserId, "relatedUserId");
            Validator.notNull(groupIndex, "groupIndex");
        } catch (ResponseException e) {
            return Mono.error(e);
        }
        Mono<DeleteResult> deleteMono = userRelationshipGroupMemberRepository
                .deleteRelatedUserFromRelationshipGroup(ownerId,
                        relatedUserId,
                        groupIndex,
                        session);
        if (updateRelationshipGroupsMembersVersion) {
            return deleteMono.flatMap(result -> {
                if (result.getDeletedCount() == 0) {
                    return Mono.empty();
                }
                return userVersionService.updateRelationshipGroupsMembersVersion(ownerId)
                        .onErrorResume(t -> {
                            LOGGER.error(
                                    "Caught an error while updating the relationship groups members version of the owner ({}) after deleting the user ({}) from the group ({})",
                                    ownerId,
                                    relatedUserId,
                                    groupIndex,
                                    t);
                            return Mono.empty();
                        })
                        .thenReturn(result);
            });
        }
        return deleteMono;
    }

    public Mono<DeleteResult> deleteRelatedUserFromAllRelationshipGroups(
            @NotNull Long ownerId,
            @NotNull Long relatedUserId,
            @Nullable ClientSession session,
            boolean updateRelationshipGroupsMembersVersion) {
        try {
            Validator.notNull(ownerId, "ownerId");
            Validator.notNull(relatedUserId, "relatedUserId");
        } catch (ResponseException e) {
            return Mono.error(e);
        }
        return deleteRelatedUsersFromAllRelationshipGroups(
                Set.of(new UserRelationship.Key(ownerId, relatedUserId)),
                session,
                updateRelationshipGroupsMembersVersion);
    }

    public Mono<DeleteResult> deleteRelatedUsersFromAllRelationshipGroups(
            @NotEmpty Set<UserRelationship.@ValidUserRelationshipKey Key> keys,
            @Nullable ClientSession session,
            boolean updateRelationshipGroupsMembersVersion) {
        try {
            Validator.notEmpty(keys, "keys");
            for (UserRelationship.Key key : keys) {
                DataValidator.validRelationshipKey(key);
            }
        } catch (ResponseException e) {
            return Mono.error(e);
        }
        Mono<DeleteResult> deleteMono;
        // fast path
        if (keys.size() == 1) {
            UserRelationship.Key key = keys.iterator()
                    .next();
            deleteMono = userRelationshipGroupMemberRepository
                    .deleteRelatedUsersFromAllRelationshipGroups(key.getOwnerId(),
                            Set.of(key.getRelatedUserId()),
                            session);
        } else {
            // slow path
            Map<Long, List<Long>> ownerIdToRelatedUserIds =
                    CollectionUtil.newMapWithExpectedSize(keys.size());
            for (UserRelationship.Key key : keys) {
                ownerIdToRelatedUserIds
                        .computeIfAbsent(key.getOwnerId(), ignored -> new LinkedList<>())
                        .add(key.getRelatedUserId());
            }
            Set<Map.Entry<Long, List<Long>>> entries = ownerIdToRelatedUserIds.entrySet();
            int size = entries.size();
            if (size == 1) {
                Map.Entry<Long, List<Long>> ownerIdAndRelatedUserIds = entries.iterator()
                        .next();
                deleteMono = userRelationshipGroupMemberRepository
                        .deleteRelatedUsersFromAllRelationshipGroups(ownerIdAndRelatedUserIds
                                .getKey(), ownerIdAndRelatedUserIds.getValue(), session);
            } else {
                List<Mono<DeleteResult>> deleteMonos = new ArrayList<>(size);
                for (Map.Entry<Long, List<Long>> ownerIdAndRelatedUserIds : entries) {
                    deleteMonos.add(userRelationshipGroupMemberRepository
                            .deleteRelatedUsersFromAllRelationshipGroups(ownerIdAndRelatedUserIds
                                    .getKey(), ownerIdAndRelatedUserIds.getValue(), session));
                }
                deleteMono = Flux.merge(deleteMonos)
                        .collect(CollectorUtil.toList(size))
                        .map(OperationResultConvertor::merge);
            }
        }
        if (updateRelationshipGroupsMembersVersion) {
            return deleteMono.flatMap(result -> {
                Set<Long> ownerIds = CollectionUtil.newSetWithExpectedSize(keys.size());
                for (UserRelationship.Key key : keys) {
                    ownerIds.add(key.getOwnerId());
                }
                return userVersionService.updateRelationshipGroupsVersion(ownerIds)
                        .onErrorResume(t -> {
                            LOGGER.error(
                                    "Caught an error while updating the relationship groups version of the owners {} after deleting users from all groups",
                                    ownerIds,
                                    t);
                            return Mono.empty();
                        })
                        .thenReturn(result);
            });
        }
        return deleteMono;
    }

    public Mono<Void> moveRelatedUserToNewGroup(
            @NotNull Long ownerId,
            @NotNull Long relatedUserId,
            @NotNull Integer currentGroupIndex,
            @NotNull Integer targetGroupIndex,
            boolean suppressIfAlreadyExistsInTargetGroup,
            @Nullable ClientSession session) {
        try {
            Validator.notNull(ownerId, "ownerId");
            Validator.notNull(relatedUserId, "relatedUserId");
            Validator.notNull(currentGroupIndex, "currentGroupIndex");
            Validator.notNull(targetGroupIndex, "targetGroupIndex");
        } catch (ResponseException e) {
            return Mono.error(e);
        }
        if (currentGroupIndex.equals(targetGroupIndex)) {
            return Mono.empty();
        }
        UserRelationshipGroupMember.Key key =
                new UserRelationshipGroupMember.Key(ownerId, currentGroupIndex, relatedUserId);
        UserRelationshipGroupMember.Key newKey =
                new UserRelationshipGroupMember.Key(ownerId, targetGroupIndex, relatedUserId);
        Mono<Void> insert = userRelationshipGroupMemberRepository
                .insert(new UserRelationshipGroupMember(newKey, new Date()), session);
        if (suppressIfAlreadyExistsInTargetGroup) {
            insert = insert.onErrorComplete(DuplicateKeyException.class);
        }
        return insert.then(userRelationshipGroupMemberRepository.deleteById(key, session))
                .then(userVersionService.updateRelationshipGroupsVersion(ownerId)
                        .onErrorResume(t -> {
                            LOGGER.error(
                                    "Caught an error while updating the relationship groups version of the owner ({}) after moving a user to a new group",
                                    ownerId,
                                    t);
                            return Mono.empty();
                        }))
                .then();
    }

    public Mono<DeleteResult> deleteRelationshipGroups() {
        return userRelationshipGroupRepository.deleteAll();
    }

    public Mono<DeleteResult> deleteRelationshipGroups(
            @NotEmpty Set<UserRelationshipGroup.@ValidUserRelationshipGroupKey Key> keys) {
        try {
            Validator.notEmpty(keys, "keys");
            for (UserRelationshipGroup.Key key : keys) {
                DataValidator.validRelationshipGroupKey(key);
            }
        } catch (ResponseException e) {
            return Mono.error(e);
        }
        return userRelationshipGroupRepository.deleteByIds(keys);
    }

    public Flux<UserRelationshipGroup> queryRelationshipGroups(
            @Nullable Set<Long> ownerIds,
            @Nullable Set<Integer> indexes,
            @Nullable Set<String> names,
            @Nullable DateRange creationDateRange,
            @Nullable Integer page,
            @Nullable Integer size) {
        return userRelationshipGroupRepository
                .findRelationshipGroups(ownerIds, indexes, names, creationDateRange, page, size);
    }

    public Mono<Long> countRelationshipGroups(
            @Nullable Set<Long> ownerIds,
            @Nullable Set<Integer> indexes,
            @Nullable Set<String> names,
            @Nullable DateRange creationDateRange) {
        return userRelationshipGroupRepository
                .countRelationshipGroups(ownerIds, indexes, names, creationDateRange);
    }

    public Mono<Long> countRelationshipGroups(
            @Nullable Set<Long> ownerIds,
            @Nullable Set<Long> relatedUserIds) {
        return userRelationshipGroupMemberRepository.countGroups(ownerIds, relatedUserIds);
    }

    public Mono<Long> countRelationshipGroupMembers(
            @Nullable Set<Long> ownerIds,
            @Nullable Set<Integer> groupIndexes) {
        return userRelationshipGroupMemberRepository.countMembers(ownerIds, groupIndexes);
    }

}