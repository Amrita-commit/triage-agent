package dev.copilot.core.approval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory {@link ApprovalStore}. Sufficient for the local-first design. */
public class InMemoryApprovalStore implements ApprovalStore {

    private final ConcurrentMap<String, ApprovalRequest> requests = new ConcurrentHashMap<>();

    @Override
    public ApprovalRequest save(ApprovalRequest request) {
        requests.put(request.id(), request);
        return request;
    }

    @Override
    public Optional<ApprovalRequest> findById(String id) {
        return Optional.ofNullable(requests.get(id));
    }

    @Override
    public List<ApprovalRequest> findAll() {
        List<ApprovalRequest> all = new ArrayList<>(requests.values());
        all.sort(Comparator.comparing(ApprovalRequest::createdAt).reversed());
        return all;
    }

    @Override
    public List<ApprovalRequest> findPending() {
        return findAll().stream().filter(r -> r.status() == ApprovalStatus.PENDING).toList();
    }
}
