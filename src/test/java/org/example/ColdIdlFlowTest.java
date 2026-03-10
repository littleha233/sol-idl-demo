package org.example;

import org.example.coldidl.biz.ColdIdlBiz;
import org.example.coldidl.biz.impl.ColdIdlBizImpl;
import org.example.coldidl.controller.ColdIdlController;
import org.example.coldidl.dto.BuildTxFromMapReq;
import org.example.coldidl.dto.BuildTxResultDto;
import org.example.coldidl.dto.ContractInfoDto;
import org.example.coldidl.dto.OperationDetailDto;
import org.example.coldidl.dto.OperationInfoDto;
import org.example.coldidl.service.SolIdlRegistryService;
import org.example.coldidl.service.impl.SolIdlRegistryServiceImpl;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColdIdlFlowTest {

    private ColdIdlController newController() throws Exception {
        SolIdlRegistryService registryService = new SolIdlRegistryServiceImpl(Path.of("testdata/contracts-config.json"));
        ColdIdlBiz biz = new ColdIdlBizImpl(registryService);
        return new ColdIdlController(biz);
    }

    @Test
    void shouldListAllSolContracts() throws Exception {
        ColdIdlController controller = newController();
        List<ContractInfoDto> contracts = controller.getContracts();

        assertEquals(2, contracts.size());
        assertTrue(contracts.stream().anyMatch(c -> "3001".equals(c.getContractId()) && "Aggregator".equals(c.getDisplayName())));
        assertTrue(contracts.stream().anyMatch(c -> "3002".equals(c.getContractId()) && "VaultGuard".equals(c.getDisplayName())));
    }

    @Test
    void shouldListOperationsByContractId() throws Exception {
        ColdIdlController controller = newController();
        List<OperationInfoDto> operations = controller.getContractOperations("3001");

        assertEquals(2, operations.size());
        assertTrue(operations.stream().anyMatch(op -> "1".equals(op.getOperationId()) && "set_safe".equals(op.getOperationKey())));
        assertTrue(operations.stream().anyMatch(op -> "2".equals(op.getOperationId()) && "set_operator".equals(op.getOperationKey())));
    }

    @Test
    void shouldReturnOperationDetailForFrontEndForm() throws Exception {
        ColdIdlController controller = newController();
        OperationDetailDto detail = controller.getOperationDetail("3002", "2");

        assertEquals("set_limit", detail.getOperationKey());
        assertEquals("SetLimit", detail.getDisplayName());
        assertEquals("daily_limit", detail.getArgFields().get(0).getName());
        assertEquals("u64", detail.getArgFields().get(0).getType());
        assertTrue(detail.getAccountFields().stream().anyMatch(a -> "authority".equals(a.getName()) && "FROM".equals(a.getSource())));
        assertTrue(detail.getAccountFields().stream().anyMatch(a -> "vault_config".equals(a.getName()) && "PDA".equals(a.getSource())));
    }

    @Test
    void shouldBuildTxFromContractIdOperationIdAndParamMap() throws Exception {
        ColdIdlController controller = newController();

        BuildTxFromMapReq req = new BuildTxFromMapReq();
        req.setContractId("3001");
        req.setOperationId("1");
        req.setFrom("8P9Dpf29HDDWwNxvAhB4XqHsVQmobGCwERXWJmbL7U2H");

        Map<String, Object> paramMap = new HashMap<String, Object>();
        paramMap.put("safe", "4a3UyD3vhpG6A19i7NiFxxnVCLQgMYAV16svekmfA6uz");
        req.setParamMap(paramMap);

        BuildTxResultDto txResult = controller.buildTx(req);

        assertNotNull(txResult);
        assertEquals("3001", txResult.getContractId());
        assertEquals("1", txResult.getOperationId());
        assertEquals("set_safe", txResult.getOperationKey());
        assertFalse(txResult.getMessageBase64().isBlank());
        assertFalse(txResult.getUnsignedLegacyTransactionBase64().isBlank());
        assertEquals(1, txResult.getRequiredSigners().size());
        assertTrue(txResult.getAccountKeys().contains("Hj1rx5gpvzbLvWXz1vxLUkcqnoKrKSfgBVMfpmJ97Hmz"));
    }
}
