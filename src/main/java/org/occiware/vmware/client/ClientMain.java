/**
 * Copyright (c) 2016 Inria
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * - Christophe Gourdin <christophe.gourdin@inria.fr>
 *
 */
package org.occiware.vmware.client;

import com.vmware.vim25.ArrayOfHostDatastoreBrowserSearchResults;
import com.vmware.vim25.Description;
import com.vmware.vim25.DynamicProperty;
import com.vmware.vim25.FileInfo;
import com.vmware.vim25.FileQuery;
import com.vmware.vim25.FileQueryFlags;
import com.vmware.vim25.GuestDiskInfo;
import com.vmware.vim25.GuestInfo;
import com.vmware.vim25.GuestNicInfo;
import com.vmware.vim25.HostDatastoreBrowserSearchResults;
import com.vmware.vim25.HostDatastoreBrowserSearchSpec;
import com.vmware.vim25.HostDhcpService;
import com.vmware.vim25.HostDiskDimensionsChs;
import com.vmware.vim25.HostNetworkInfo;
import com.vmware.vim25.HostVirtualNic;
import com.vmware.vim25.HostVirtualNicSpec;
import com.vmware.vim25.InvalidProperty;
import com.vmware.vim25.NetDhcpConfigInfo;
import com.vmware.vim25.NetDhcpConfigInfoDhcpOptions;
import com.vmware.vim25.RuntimeFault;
import com.vmware.vim25.TaskInfo;
import com.vmware.vim25.VirtualAHCIController;
import com.vmware.vim25.VirtualDevice;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualDeviceConnectInfo;
import com.vmware.vim25.VirtualDeviceFileBackingInfo;
import com.vmware.vim25.VirtualDisk;
import com.vmware.vim25.VirtualE1000;
import com.vmware.vim25.VirtualE1000e;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualHardware;
import com.vmware.vim25.VirtualIDEController;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VirtualMachineQuickStats;
import com.vmware.vim25.VirtualMachineToolsStatus;
import com.vmware.vim25.VirtualPCNet32;
import com.vmware.vim25.VirtualSCSIController;
import com.vmware.vim25.VirtualVmxnet;
import com.vmware.vim25.VirtualVmxnet2;
import com.vmware.vim25.VirtualVmxnet3;
import com.vmware.vim25.VmDiskFileQuery;
import com.vmware.vim25.VmDiskFileQueryFilter;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.FileManager;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.Network;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualDiskManager;
import com.vmware.vim25.mo.VirtualMachine;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main class for testing connector operations.
 *
 * @author Christophe Gourdin - Inria
 */
public class ClientMain {

    static final String SERVER_NAME = "your server address";
    static final String USER_NAME = "your user";
    static final String PASSWORD = "your password";
    private static final String HOSTNAME = "your hostname";

    public static void main(String[] args) {
        String url = "https://" + SERVER_NAME + "/sdk";
        
        try {
            ServiceInstance si = new ServiceInstance(new URL(url), USER_NAME, PASSWORD, true);
            System.out.println("Vcenter Name:" + si.getAboutInfo().name);
            System.out.println("Connected to service.");

            HostSystem host = (HostSystem) new InventoryNavigator(si.getRootFolder()).searchManagedEntity("HostSystem", HOSTNAME);

            for (Network net : host.getNetworks()) {
                System.out.println("Network of the host : " + net.getName());
            }

            HostNetworkInfo info = host.getConfig().getNetwork();
            if (info != null) {
                if (info.getDhcp() != null) {
                    for (HostDhcpService service : info.getDhcp()) {
                        String subnetAddr = service.getSpec().getIpSubnetAddr();
                        String subnetMask = service.getSpec().getIpSubnetMask();
                        String vswitch = service.getSpec().getVirtualSwitch();
                        System.out.println("Host : " + HOSTNAME);
                        System.out.println("VSwitch : " + vswitch);
                        System.out.println("Subnetmask : " + subnetMask);
                        System.out.println("subnetAddr = " + subnetAddr);
                    }
                }

                if (info.getConsoleVnic() != null) {
                    HostVirtualNic[] vnics = info.getConsoleVnic();
                    for (HostVirtualNic vnic : vnics) {
                        String device = vnic.getDevice();
                        String key = vnic.getKey();
                        String port = vnic.getPort();
                        String portGroup = vnic.getPortgroup();
                        HostVirtualNicSpec nicSpec = vnic.getSpec();

                        System.out.println("Device: " + device);
                        System.out.println("key: " + key);
                        System.out.println("port: " + port);
                        System.out.println("Port group : " + portGroup);
                        if (nicSpec != null) {
                            System.out.println("Nic spec: " + nicSpec.toString());
                        }
                    }
                }
            }
            ManagedEntity[] managedEntities = new InventoryNavigator(host).searchManagedEntities("VirtualMachine");

            for (int i = 0; i < managedEntities.length; i++) {
                VirtualMachine vm = (VirtualMachine) managedEntities[i];
                writeVmInfo(vm);
                System.out.println();
            }
            Folder rootFolder = si.getRootFolder();
            System.out.println(" --< Reading datastores informations...");

            // Get datacenter info.
            Datacenter dc = null;
            ManagedEntity[] dcs = new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter");
            List<Datastore> dss = new ArrayList<>();
            if (dcs != null && dcs.length > 0) {
                for (ManagedEntity entity : dcs) {
                    if (entity instanceof Datacenter) {
                        dc = (Datacenter) entity;
                        System.out.println("Datacenter found: " + dc.getName());
                        System.out.println("Network folder found : " + dc.getNetworkFolder().getName());

                        for (Datastore ds : dc.getDatastores()) {
                            System.out.println("Datastore found : " + ds.getName());
                            dss.add(ds);
                        }
                    }
                }
            }

            if (!dss.isEmpty()) {
                for (Datastore ds : dss) {

                    FileQuery[] fileQueries = ds.getBrowser().getSupportedType();
                    for (FileQuery fileQuery : fileQueries) {
                        System.out.println("File query supported dynamic type: " + fileQuery.getDynamicType() + " --< " + fileQuery.toString());
                        if (fileQuery instanceof VmDiskFileQuery) {
                            VmDiskFileQuery vmDiskFileQuery = (VmDiskFileQuery) fileQuery;
                            System.out.println("vmDiskFileQuery detected " + vmDiskFileQuery.toString());
                        }
                    }
                    String dsName = ds.getName();
                    System.out.println("\nSearching The Datastore " + dsName);

                    VmDiskFileQueryFilter vdiskFilter = new VmDiskFileQueryFilter();
                    vdiskFilter.setControllerType(new String[]{"VirtualSCSIController"}); // "VirtualIDEController", "VirtualSATAController",

                    VmDiskFileQuery fQuery = new VmDiskFileQuery();
                    fQuery.setFilter(vdiskFilter);

                    HostDatastoreBrowserSearchSpec searchSpec = new HostDatastoreBrowserSearchSpec();
                    searchSpec.setQuery(new FileQuery[]{fQuery});
                    // searchSpec.setMatchPattern(new String[] {"sdk*.*"});

                    FileQueryFlags fqf = new FileQueryFlags();
                    fqf.setFileSize(true);
                    fqf.setModification(true);
                    fqf.setFileOwner(true);
                    fqf.setFileType(true);
                    searchSpec.setDetails(fqf);

//                    Task dsPathTask = ds.getBrowser().searchDatastore_Task("["+ ds.getName() + "]", null);
//                    try {
//                        dsPathTask.waitForTask();
//                        TaskInfo tInfo = dsPathTask.getTaskInfo();
//                        HostDatastoreBrowserSearchResults searchResult
//                                = (HostDatastoreBrowserSearchResults) tInfo.getResult();
//                        
//                        if (searchResult == null) {
//                            System.out.println("No results, no datastore path on this datastore");
//                            return;
//                        }
//                        
//                        String folderPath = searchResult.getFolderPath();
//                        System.out.println("Folder path --> " + folderPath);
//                        
//                        FileInfo[] files = searchResult.getFile();
//                        
//                        if (files == null) {
//                            System.out.println("No results, no root files on this datastore");
//                            return;
//                        }
//                        
//                        for (FileInfo fileInfo : files) {
//                            System.out.println("File --<  " + fileInfo.getPath());
//                            System.out.println("owner: " + fileInfo.getOwner());
//                            System.out.println("Dynamic type: " + fileInfo.getDynamicType());
//                            System.out.println(" ---< with size : " + fileInfo.getFileSize());
//                        }
//                        
//                    } catch (RemoteException | InterruptedException ex) {
//                        ex.printStackTrace();
//                    }
                    Task subFolderTask = ds.getBrowser().searchDatastoreSubFolders_Task("[" + ds.getName() + "]", searchSpec); // searchSpec
                    try {
                        subFolderTask.waitForTask();
                        TaskInfo tInfo = subFolderTask.getTaskInfo();
                        ArrayOfHostDatastoreBrowserSearchResults searchResult
                                = (ArrayOfHostDatastoreBrowserSearchResults) tInfo.getResult();

                        HostDatastoreBrowserSearchResults[] results = null;
                        if (searchResult == null) {
                            System.out.println("No results, no scsi disk files on this datastore");
                            return;
                        }
                        results = searchResult.getHostDatastoreBrowserSearchResults();

                        if (results == null) {
                            System.out.println("No results, no scsi disk files on this datastore");
                            return;
                        }
                        int len = searchResult.getHostDatastoreBrowserSearchResults().length;
                        String fullPath;
                        String basePath;
                        for (int j = 0; j < len; j++) {
                            HostDatastoreBrowserSearchResults sres = searchResult.HostDatastoreBrowserSearchResults[j];
                            System.out.println("Folder path: " + sres.getFolderPath());
                            basePath = sres.getFolderPath();
                            FileInfo[] fileArray = sres.getFile();
                            if (fileArray == null) {
                                continue;
                            }

                            for (FileInfo fileInfo : fileArray) {

                                System.out.println("Virtual Disks Files --<  " + fileInfo.getPath());
                                System.out.println("owner: " + fileInfo.getOwner());
                                System.out.println("Dynamic type: " + fileInfo.getDynamicType());
                                System.out.println(" ---< with size : " + fileInfo.getFileSize());

                                System.out.println("Full path -----< " + sres.getFolderPath() + fileInfo.getPath());
                                fullPath = basePath + fileInfo.getPath();
                                VirtualDiskManager virtDiskMgr = si.getVirtualDiskManager();
                                if (virtDiskMgr == null) {
                                    System.out.println("VirtualDisk manager is not available !!!");
                                } else {
                                    System.out.println("VirtualDisk manager is available.");
                                    String uuid = virtDiskMgr.queryVirtualDiskUuid(fullPath, dc);
                                    System.out.println("uuid=" + uuid);
                                    HostDiskDimensionsChs dims = virtDiskMgr.queryVirtualDiskGeometry(fullPath, dc);
                                    System.out.println("Cylinder: " + dims.getCylinder());
                                    System.out.println("Head    : " + dims.getHead());
                                    System.out.println("Sector  : " + dims.getSector());
                                }
                            }
                        }
                    } catch (RemoteException | InterruptedException ex) {
                    }

                }
            }
            FileManager fileMgr = si.getFileManager();
            if (fileMgr == null) {
                System.out.println("The file manager is not available !!!");
            } else {
                System.out.println("The file manager is available.");
            }
            if (si.getAlarmManager() == null) {
                System.out.println("The alarm manager is not available !!!");
            } else {
                System.out.println("The alarm manager is available.");
            }
            if (si.getAccountManager() == null) {
                System.out.println("The account manager is not available !!!");
            } else {
                System.out.println("The account manager is available.");
            }
            if (si.getClusterProfileManager() == null) {
                System.out.println("Cluster profile manager is not available !!!");
            } else {
                System.out.println("Cluster profile manager is available.");
            }
            if (si.getCustomFieldsManager() == null) {
                System.out.println("Custom fields manager is not available !!!");
            } else {
                System.out.println("Custom fields manager is available.");
            }
            if (si.getCustomizationSpecManager() == null) {
                System.out.println("Customization spec manager is not available !!!");
            } else {
                System.out.println("Customization spec manager is available.");
            }
            if (si.getDiagnosticManager() == null) {
                System.out.println("Diagnostic manager is not available !!!");
            } else {
                System.out.println("Diagnostic manager is available.");
            }
            if (si.getDistributedVirtualSwitchManager() == null) {
                System.out.println("Distributed virtual switch manager is not available !!!");
            } else {
                System.out.println("Distributed virtual switch manager is available.");
            }
            if (si.getEventManager() == null) {
                System.out.println("Event manager is not available !!!");
            } else {
                System.out.println("Event manager is available.");
            }
            if (si.getExtensionManager() == null) {
                System.out.println("Extension manager is not available !!!");
            } else {
                System.out.println("Extension manager is available.");
            }
            if (si.getGuestOperationsManager() == null) {
                System.out.println("Guest operations manager is not available !!!");
            } else {
                System.out.println("Guest operations manager is available.");
            }
            if (si.getHostProfileManager() == null) {
                System.out.println("Host profile manager is not available !!!");
            } else {
                System.out.println("Host profile manager is available.");
            }
            if (si.getHostSnmpSystem() == null) {
                System.out.println("Host snmp system is not available !!!");
            } else {
                System.out.println("Host snmp system is available.");
            }
            if (si.getIoFilterManager() == null) {
                System.out.println("IO Filter manager is not available !!!");
            } else {
                System.out.println("IO Filter manager is available.");
            }
            if (si.getIpPoolManager() == null) {
                System.out.println("IP Pool manager is not available !!!");
            } else {
                System.out.println("IP Pool manager is available.");
            }
            if (si.getLicenseManager() == null) {
                System.out.println("Licence manager is not available !!!");
            } else {
                System.out.println("Licence manager is available.");
            }
            if (si.getLocalizationManager() == null) {
                System.out.println("Localization manager is not available !!!");
            } else {
                System.out.println("Localization manager is available.");
            }
            if (si.getOptionManager() == null) {
                System.out.println("Option manager is not available !!!");
            } else {
                System.out.println("Option manager is available.");
            }
            if (si.getOvfManager() == null) {
                System.out.println("OVF manager is not available !!!");
            } else {
                System.out.println("OVF manager is available.");
            }
            if (si.getPerformanceManager() == null) {
                System.out.println("Performance manager is not available !!!");
            } else {
                System.out.println("Performance manager is available.");
            }
            if (si.getViewManager() == null) {
                System.out.println("View manager is not available !!!");
            } else {
                System.out.println("View manager is available.");
            }

            VirtualDiskManager virtDiskMgr = si.getVirtualDiskManager();
            if (virtDiskMgr == null) {
                System.out.println("VirtualDisk manager is not available !!!");
            } else {
                System.out.println("VirtualDisk manager is available.");
//                fileVMDKPath = "[datastore1] /Occiware Tuto2/"
//                        + "Occiware Tuto2.vmdk";
//                String uuid = virtDiskMgr.queryVirtualDiskUuid(fileVMDKPath, dc);
//                System.out.println("uuid=" + uuid);

            }

            si.getServerConnection().logout();
        } catch (InvalidProperty e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (RuntimeFault e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static void writeVmInfo(VirtualMachine vm) {
        System.out.println("Virtual Machine:" + vm.getName());
        VirtualMachineConfigInfo config = vm.getConfig();
        VirtualHardware hw = config.getHardware();
        System.out.println("Memory in MB: " + hw.getMemoryMB());
        System.out.println("# of CPUs: " + hw.getNumCPU());
        System.out.println("Number of core per socket : " + hw.getNumCoresPerSocket());
        if (hw.getDynamicProperty() != null) {
            for (DynamicProperty dn : hw.getDynamicProperty()) {
                String name = dn.getName();
                Object value = dn.getVal();
                System.out.println("Dynamic property: Name --> " + name);
                System.out.println("Value object : " + value.toString());
            }
        }
        // Architecture.
        String guestFullName = vm.getConfig().getGuestFullName();
        if (guestFullName != null && guestFullName.contains("32")) {
            System.out.println("architecture : x86");
        }
        if (guestFullName != null && guestFullName.contains("64")) {
            System.out.println("architecture : x64");
        }
        System.out.println("quickstats: ");
        VirtualMachineQuickStats qstats = vm.getSummary().getQuickStats();
        System.out.println("Overall CPU demand : " + qstats.getOverallCpuDemand());
        System.out.println("Overall CPU usage : " + qstats.getOverallCpuUsage());
        System.out.println("CPU distributed entitlement : " + qstats.getDistributedCpuEntitlement());
        System.out.println("CPU static entitlement : " + qstats.getStaticCpuEntitlement());

        VirtualDevice[] devices = hw.getDevice();
        for (int i = 0; i < devices.length; i++) {
            VirtualDevice device = devices[i];
            Description deviceInfo = device.getDeviceInfo();
            System.out.println("Device (" + device.getKey() + "): " + deviceInfo.getLabel() + " : " + deviceInfo.getSummary());
            if (device instanceof VirtualDisk) {
                VirtualDisk disk = (VirtualDisk) device;
                System.out.println("VirtualDisk id : " + disk.getDiskObjectId());
                // System.out.println("VirtualDisk " + disk.get)
                VirtualDeviceBackingInfo vdbi = device.getBacking();
                System.out.println("Unit number : " + disk.getUnitNumber());
                String diskName;
                if (vdbi instanceof VirtualDeviceFileBackingInfo) {
                    diskName = ((VirtualDeviceFileBackingInfo) vdbi).getFileName();
                    System.out.println("Disk file name (vmdk) : " + diskName);
                }

                Integer controllerKey = disk.getControllerKey();
                System.out.println("Controller key: " + controllerKey);
                // Get the controller information.
                for (VirtualDevice dev : devices) {
                    if (dev instanceof VirtualAHCIController) {
                        VirtualAHCIController ahciController = (VirtualAHCIController) dev;
                        System.out.println("AHCI controller detected !!!");

                    } else if (dev instanceof VirtualSCSIController) {
                        VirtualSCSIController scsiController = (VirtualSCSIController) dev;
                        System.out.println("SCSI controller detected !!!");

                    } else if (dev instanceof VirtualIDEController) {
                        VirtualIDEController ideController = (VirtualIDEController) dev;
                        System.out.println("IDE controller detected !!!");
                    }
                }

            } // Endif type virtual disk.
            if (device instanceof VirtualEthernetCard) {
                VirtualEthernetCard vEth = (VirtualEthernetCard) device;
                VirtualDeviceBackingInfo properties = vEth.getBacking();
                VirtualEthernetCardNetworkBackingInfo nicBacking = (VirtualEthernetCardNetworkBackingInfo) properties;
                System.out.println("Current NIC backing device name: " + nicBacking.getDeviceName());
                String addressType = vEth.getAddressType();
                System.out.println("Address Type : " + addressType);
                VirtualDeviceConnectInfo conn = vEth.getConnectable();
                if (conn != null) {
                    String connectionStatus = conn.getStatus();
                    System.out.println("Connection status: " + connectionStatus);
                }
                String devLabel = vEth.getDeviceInfo().getLabel();
                System.out.println("Device label : " + devLabel);
                String devSummary = vEth.getDeviceInfo().getSummary();
                System.out.println("device summary : " + devSummary);
                String externalId = vEth.getExternalId();
                System.out.println("Device external id: " + externalId);

                DynamicProperty[] propert = nicBacking.getDynamicProperty();

                if (propert != null) {
                    for (DynamicProperty proper : propert) {
                        String name = proper.getName();
                        Object value = proper.getVal();
                        System.out.println("Dynamic property : Key: " + name + " --< value: " + value.toString());
                    }
                }
                String[] ipAddressesLocal;
                String ipAddressPlainLocal = "";
                String dns;
                GuestNicInfo[] guestNicInf = vm.getGuest().getNet();
                int j;
                if (guestNicInf != null) {
                    for (GuestNicInfo nicInfo : guestNicInf) {
                        ipAddressesLocal = nicInfo.getIpAddress();
                        j = 0;
                        for (String ipAddress : ipAddressesLocal) {
                            j++;
                            System.out.println("IP ADDRESS: " + ipAddress);
                            if (j == ipAddressesLocal.length) {
                                ipAddressPlainLocal += ipAddress;
                            } else {
                                ipAddressPlainLocal += ipAddress + ";";
                            }
                        }
                        boolean dhcp = true;
                        NetDhcpConfigInfo dhcpConfig = nicInfo.getIpConfig().getDhcp();
                        if (dhcpConfig == null) {
                            System.out.println("dhcp config is null !!!!");
                        } else {
                            NetDhcpConfigInfoDhcpOptions options = dhcpConfig.getIpv4();
                            if (options == null) {
                                System.out.println("options is null !!!");
                            } else {
                                dhcp = options.isEnable();
                                if (dhcp) {
                                    System.out.println("dhcp enabled");
                                } else {
                                    System.out.println("dhcp disabled");
                                }
                            }
                        }

                    }
                }
                if (ipAddressPlainLocal.isEmpty()) {
                    System.out.println("No ip address setup.");
                } else {
                    System.out.println("Ip addresses :-->  " + ipAddressPlainLocal);
                }
                if (vEth instanceof VirtualE1000) {
                    System.out.println("Adapter type: E1000");
                } else if (vEth instanceof VirtualE1000e) {
                    System.out.println("Adapter type: E1000E");
                } else if (vEth instanceof VirtualPCNet32) {
                    System.out.println("Adapter type: PCnet32");
                } else if (vEth instanceof VirtualVmxnet) {
                    System.out.println("Adapter type: Vmxnet");
                } else if (vEth instanceof VirtualVmxnet2) {
                    System.out.println("Adapter type: Vmxnet2");
                } else if (vEth instanceof VirtualVmxnet3) {
                    System.out.println("Adapter type: Vmxnet3");
                }
                System.out.println("connected: " + vEth.getConnectable().connected);
                System.out.println("Connect at power on: " + vEth.getConnectable().startConnected);
                System.out.println("Manual: " + vEth.addressType);
                System.out.println("MAC address:" + vEth.macAddress);

            }
        }

        // Check if vm is a template.
        if (vm.getConfig().isTemplate()) {
            System.out.println("This virtual machine is a template !!!");
        }
        System.out.println("GuestOSId : " + vm.getConfig().getGuestId() + " --> " + vm.getConfig().getGuestFullName());

        // State.
        String guestState = vm.getGuest().getGuestState();
        String vmState = vm.getSummary().getRuntime().getPowerState().name();
        String overallStatus = vm.getOverallStatus().name();
        System.out.println("Guest state : " + guestState);        
        System.out.println("VM State : " + vmState);
        System.out.println("Overall status: " + overallStatus);
        
        System.out.println("---------------------------------------");
        System.out.println("Guest system information");
        System.out.println("---------------------------------------");
        if (vm.getGuest().getToolsStatus().equals(VirtualMachineToolsStatus.toolsOk)) {
            System.out.println("System has vmware tools installed and running.");
            GuestInfo vmGuestInfo = vm.getGuest();
            
            System.out.println("App State : " + vmGuestInfo.getAppState());
            GuestDiskInfo[] gDiskInfo = vmGuestInfo.getDisk();
            if (gDiskInfo != null) {
                for (GuestDiskInfo info : gDiskInfo) {
                    Long capacity = info.getCapacity();
                    Long freespace = info.getFreeSpace();
                    String diskPath = info.getDiskPath();
                    System.out.println("disk storage capacity (from guest os) : " + capacity);
                    System.out.println("disk storage freespace : " + freespace);
                    System.out.println("disk storage path : " + diskPath);
                    
                }
            }
            
            System.out.println("Hostname : " + vmGuestInfo.getHostName());
            
            
        }
        
        
    }

}
