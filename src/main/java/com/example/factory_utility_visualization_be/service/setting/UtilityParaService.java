package com.example.factory_utility_visualization_be.service.setting;



import com.example.factory_utility_visualization_be.model.F2UtilityPara;
import com.example.factory_utility_visualization_be.repository.F2UtilityParaRepo;
import com.example.factory_utility_visualization_be.request.setting.UtilityParaRequest;
import com.example.factory_utility_visualization_be.response.setting.UtilityParaResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UtilityParaService {

	private final F2UtilityParaRepo repository;

	public UtilityParaService(F2UtilityParaRepo repository) {
		this.repository = repository;
	}

	// ===== GET ALL =====
	public List<UtilityParaResponse> getAll() {
		return repository.findAll()
				.stream()
				.map(this::toResponse)
				.toList();
	}

	// ===== GET BY ID =====
	public UtilityParaResponse getById(Long id) {
		F2UtilityPara entity = repository.findById(id)
				.orElseThrow(() -> new RuntimeException("UtilityPara not found with id: " + id));
		return toResponse(entity);
	}

	// ===== CREATE =====
	public UtilityParaResponse create(UtilityParaRequest request) {
		F2UtilityPara entity = new F2UtilityPara();
		mapRequestToEntity(request, entity);
		return toResponse(repository.save(entity));
	}

	// ===== UPDATE =====
	public UtilityParaResponse update(Long id, UtilityParaRequest request) {
		F2UtilityPara entity = repository.findById(id)
				.orElseThrow(() -> new RuntimeException("UtilityPara not found with id: " + id));

		mapRequestToEntity(request, entity);
		return toResponse(repository.save(entity));
	}

	// ===== DELETE =====
	public void delete(Long id) {
		F2UtilityPara entity = repository.findById(id)
				.orElseThrow(() -> new RuntimeException("UtilityPara not found with id: " + id));
		repository.delete(entity);
	}

	// ===== FILTER =====
	public List<UtilityParaResponse> getByBoxDeviceId(String boxDeviceId) {
		return repository.findByBoxDeviceId(boxDeviceId)
				.stream().map(this::toResponse).toList();
	}

	public List<UtilityParaResponse> getByCateId(String cateId) {
		return repository.findByCateId(cateId)
				.stream().map(this::toResponse).toList();
	}

	public List<UtilityParaResponse> getByImportant(Integer isImportant) {
		return repository.findByIsImportant(isImportant)
				.stream().map(this::toResponse).toList();
	}

	public List<UtilityParaResponse> getByAlert(Integer  isAlert) {
		return repository.findByIsAlert(isAlert)
				.stream().map(this::toResponse).toList();
	}

	// ===== MAPPING =====
	private void mapRequestToEntity(UtilityParaRequest req, F2UtilityPara e) {
		if (req.getBoxDeviceId() != null) e.setBoxDeviceId(req.getBoxDeviceId());
		if (req.getPlcAddress() != null) e.setPlcAddress(req.getPlcAddress());
		if (req.getValueType() != null) e.setValueType(req.getValueType());
		if (req.getUnit() != null) e.setUnit(req.getUnit());
		if (req.getCateId() != null) e.setCateId(req.getCateId());
		if (req.getNameVi() != null) e.setNameVi(req.getNameVi());
		if (req.getNameEn() != null) e.setNameEn(req.getNameEn());
		if (req.getIsImportant() != null) e.setIsImportant(req.getIsImportant());
		if (req.getIsAlert() != null) e.setIsAlert(req.getIsAlert());
		if (req.getMinAlert() != null) e.setMinAlert(req.getMinAlert());
		if (req.getMaxAlert() != null) e.setMaxAlert(req.getMaxAlert());
	}

	private UtilityParaResponse toResponse(F2UtilityPara e) {
		UtilityParaResponse r = new UtilityParaResponse();
		r.setId(e.getId());
		r.setBoxDeviceId(e.getBoxDeviceId());
		r.setPlcAddress(e.getPlcAddress());
		r.setValueType(e.getValueType());
		r.setUnit(e.getUnit());
		r.setCateId(e.getCateId());
		r.setNameVi(e.getNameVi());
		r.setNameEn(e.getNameEn());
		r.setIsImportant(e.getIsImportant());
		r.setIsAlert(e.getIsAlert());
		r.setMinAlert(e.getMinAlert());
		r.setMaxAlert(e.getMaxAlert());
		return r;
	}
}