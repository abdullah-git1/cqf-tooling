package org.opencds.cqf.tooling.utilities;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.opencds.cqf.tooling.common.CqfmSoftwareSystem;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;

public class BundleUtils {

    public static Object bundleArtifacts(String id, List<IBaseResource> resources, FhirContext fhirContext) {
        for (IBaseResource resource : resources) {
            if (resource.getIdElement().getIdPart() == null || resource.getIdElement().getIdPart().equals("")) {
                ResourceUtils.setIgId(id.replace("-bundle", "-" + UUID.randomUUID()), resource, false);
                resource.setId(resource.getClass().getSimpleName() + "/" + resource.getIdElement().getIdPart());
            }
        }

        switch (fhirContext.getVersion().getVersion()) {
            case DSTU3:
                return bundleStu3Artifacts(id, resources);
            case R4:
                return bundleR4Artifacts(id, resources);
            default:
                throw new IllegalArgumentException("Unknown fhir version: " + fhirContext.getVersion().getVersion().getFhirVersionString());
        }
    }

    public static org.hl7.fhir.dstu3.model.Bundle bundleStu3Artifacts(String id, List<IBaseResource> resources) {
        org.hl7.fhir.dstu3.model.Bundle bundle = new org.hl7.fhir.dstu3.model.Bundle();
        ResourceUtils.setIgId(id, bundle, false);
        bundle.setType(org.hl7.fhir.dstu3.model.Bundle.BundleType.TRANSACTION);
        for (IBaseResource resource : resources) {
            bundle.addEntry(
                    new org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent()
                            .setResource((org.hl7.fhir.dstu3.model.Resource) resource)
                            .setRequest(
                                    new org.hl7.fhir.dstu3.model.Bundle.BundleEntryRequestComponent()
                                            .setMethod(org.hl7.fhir.dstu3.model.Bundle.HTTPVerb.PUT)
                                            .setUrl(((org.hl7.fhir.dstu3.model.Resource) resource).getId())
                            )
            );
        }
        return bundle;
    }

    public static org.hl7.fhir.r4.model.Bundle bundleR4Artifacts(String id, List<IBaseResource> resources) {
        org.hl7.fhir.r4.model.Bundle bundle = new org.hl7.fhir.r4.model.Bundle();
        ResourceUtils.setIgId(id, bundle, false);
        bundle.setType(org.hl7.fhir.r4.model.Bundle.BundleType.TRANSACTION);
        for (IBaseResource resource : resources) {
            String resourceRef = (resource.getIdElement().getResourceType() == null) ? resource.fhirType() + "/" + resource.getIdElement().getIdPart() : resource.getIdElement().getValueAsString();
            bundle.addEntry(
                    new org.hl7.fhir.r4.model.Bundle.BundleEntryComponent()
                            .setResource((org.hl7.fhir.r4.model.Resource) resource)
                            .setRequest(
                                    new org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent()
                                            .setMethod(org.hl7.fhir.r4.model.Bundle.HTTPVerb.PUT)
                                            .setUrl(resourceRef)
                            )
            );
        }
        return bundle;
    }

    public static void postBundle(IOUtils.Encoding encoding, FhirContext fhirContext, String fhirUri, IBaseResource bundle, int numberOfEntries) {
        if (fhirUri != null && !fhirUri.equals("")) {
            ArrayList<IBaseResource> bundleList;
            switch (fhirContext.getVersion().getVersion()) {
                case DSTU3:
                    bundleList = breakUpStu3Bundle(bundle, numberOfEntries);
                    postSingleBundle(bundleList, encoding, fhirContext, fhirUri);
                    break;
                case R4:
                    bundleList = breakUpR4Bundle(bundle, numberOfEntries);
                    postSingleBundle(bundleList, encoding, fhirContext, fhirUri);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown fhir version: " + fhirContext.getVersion().getVersion().getFhirVersionString());
            }
         }
    }

    private static void postSingleBundle(List<IBaseResource>bundleList, IOUtils.Encoding encoding, FhirContext fhirContext, String fhirUri){
        String bundleID = " ";
        try {
            for (IBaseResource bundle: bundleList) {
                bundle.getIdElement().getIdPart();
                HttpClientUtils.post(fhirUri, bundle, encoding, fhirContext);
            }
        } catch (IOException e) {
            e.printStackTrace();
            LogUtils.putException(bundleID, "Error posting to FHIR Server: " + fhirUri + ".  Bundle not posted.");
        }

    }

    private static ArrayList<IBaseResource> breakUpR4Bundle(IBaseResource bundle, int numberOfEntries) {
        ArrayList<IBaseResource> bundleList = new ArrayList<>();
        org.hl7.fhir.r4.model.Bundle oldBundle = (org.hl7.fhir.r4.model.Bundle)bundle;
        List<org.hl7.fhir.r4.model.Bundle.BundleEntryComponent> oldEntryList = oldBundle.getEntry();
        int entryCount = 0;
        int bundleCount = 0;
        int totalEntryCount = 0;
        org.hl7.fhir.r4.model.Bundle currentNewBundle = new Bundle();
        for (org.hl7.fhir.r4.model.Bundle.BundleEntryComponent entry: oldEntryList) {
            entryCount++;
            totalEntryCount++;
            if(entryCount == 1){
                bundleCount++;
                currentNewBundle = new Bundle();
                currentNewBundle.setType(Bundle.BundleType.TRANSACTION);
                currentNewBundle.setId(oldBundle.getId() + bundleCount);
            }
            currentNewBundle.addEntry(entry);
            if(entryCount == numberOfEntries ||
                totalEntryCount == oldEntryList.size()){
                bundleList.add(currentNewBundle);
            }
            if(entryCount >= numberOfEntries){
                entryCount = 0;
            }
        }

        return bundleList;
    }

    private static ArrayList<IBaseResource> breakUpStu3Bundle(IBaseResource bundle, int numberOfEntries) {
        ArrayList<IBaseResource> bundleList = new ArrayList<>();
        org.hl7.fhir.dstu3.model.Bundle oldBundle = (org.hl7.fhir.dstu3.model.Bundle)bundle;
        List<org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent> oldEntryList = oldBundle.getEntry();
        int entryCount = 0;
        int bundleCount = 0;
        org.hl7.fhir.dstu3.model.Bundle currentNewBundle = new org.hl7.fhir.dstu3.model.Bundle();
        for (org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent entry: oldEntryList) {
            entryCount++;
            if(entryCount == 1){
                bundleCount++;
                currentNewBundle = new org.hl7.fhir.dstu3.model.Bundle();
                currentNewBundle.setType(org.hl7.fhir.dstu3.model.Bundle.BundleType.TRANSACTION);
                currentNewBundle.setId(oldBundle.getId() + bundleCount);
            }
            currentNewBundle.addEntry(entry);
            if(entryCount >= numberOfEntries){
                bundleList.add(currentNewBundle);
                entryCount = 0;
            }
        }

        return bundleList;
    }

    public static List<Map.Entry<String, IBaseResource>> GetBundlesInDir(String directoryPath, FhirContext fhirContext) {
        return GetBundlesInDir(directoryPath, fhirContext, true);
    }

    public static List<Map.Entry<String, IBaseResource>> GetBundlesInDir(String directoryPath, FhirContext fhirContext, Boolean recursive) {
        File dir = new File(directoryPath);
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("path to directory must be an existing directory.");
        }

        List<String> filePaths = IOUtils.getFilePaths(directoryPath, recursive).stream().filter(x -> !x.endsWith(".cql")).collect(Collectors.toList());

        List<Map.Entry<String, IBaseResource>> bundleMap = new ArrayList<>();
        RuntimeResourceDefinition bundleDefinition = ResourceUtils.getResourceDefinition(fhirContext, "Bundle");
        String bundleClassName = bundleDefinition.getImplementingClass().getName();
        for (String path : filePaths) {
            IBaseResource resource = IOUtils.readResource(path, fhirContext);
            if (resource != null) {
                if (bundleClassName.equals(resource.getClass().getName())) {
                    Map.Entry<String, IBaseResource> bundleEntry = new AbstractMap.SimpleEntry<>(path, resource);
                    bundleMap.add(bundleEntry);
                }
            }
        }

        return bundleMap;
//        List<IBaseResource> resources = IOUtils.readResources(filePaths, fhirContext);
//
//        return resources.stream()
//            .filter(entry -> entry != null)
//            .filter(entry ->  bundleClassName.equals(entry.getClass().getName()))
//            .collect(Collectors.toList());
    }

    public static void stampDstu3BundleEntriesWithSoftwareSystems(org.hl7.fhir.dstu3.model.Bundle bundle, List<CqfmSoftwareSystem> softwareSystems, FhirContext fhirContext, String rootDir) {
        for (org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            org.hl7.fhir.dstu3.model.Resource resource = entry.getResource();
            if ((resource.fhirType().equals("Library")) || ((resource.fhirType().equals("Measure")))) {
                org.opencds.cqf.tooling.common.stu3.CqfmSoftwareSystemHelper cqfmSoftwareSystemHelper = new org.opencds.cqf.tooling.common.stu3.CqfmSoftwareSystemHelper(rootDir);
                cqfmSoftwareSystemHelper.ensureSoftwareSystemExtensionAndDevice((org.hl7.fhir.dstu3.model.DomainResource) resource, softwareSystems, fhirContext);
            }
        }
    }

    public static void stampR4BundleEntriesWithSoftwareSystems(org.hl7.fhir.r4.model.Bundle bundle, List<CqfmSoftwareSystem> softwareSystems, FhirContext fhirContext, String rootDir) {
        for (org.hl7.fhir.r4.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            org.hl7.fhir.r4.model.Resource resource = entry.getResource();
            if ((resource.fhirType().equals("Library")) || ((resource.fhirType().equals("Measure")))) {
                org.opencds.cqf.tooling.common.r4.CqfmSoftwareSystemHelper cqfmSoftwareSystemHelper = new org.opencds.cqf.tooling.common.r4.CqfmSoftwareSystemHelper(rootDir);
                cqfmSoftwareSystemHelper.ensureSoftwareSystemExtensionAndDevice((org.hl7.fhir.r4.model.DomainResource) resource, softwareSystems, fhirContext);
            }
        }
    }

    public static void extractStu3Resources(org.hl7.fhir.dstu3.model.Bundle bundle, String encoding, String outputPath, boolean suppressNarrative) {
        FhirContext context = FhirContext.forDstu3();
        for (org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            org.hl7.fhir.dstu3.model.Resource entryResource = entry.getResource();
            if (entryResource != null) {
                if (entryResource.fhirType().equals("Measure") && suppressNarrative) {
                    ((org.hl7.fhir.dstu3.model.Measure) entryResource).setText(null);
                }
                ResourceUtils.outputResource(entryResource, encoding, context, outputPath);
            }
        }
    }

    public static void extractR4Resources(org.hl7.fhir.r4.model.Bundle bundle, String encoding, String outputPath, boolean suppressNarrative) {
        FhirContext context = FhirContext.forR4();
        for (org.hl7.fhir.r4.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            org.hl7.fhir.r4.model.Resource entryResource = entry.getResource();
            if (entryResource != null) {
                if (entryResource.fhirType().equals("Measure") && suppressNarrative) {
                    ((org.hl7.fhir.r4.model.Measure) entryResource).setText(null);
                }
                ResourceUtils.outputResource(entryResource, encoding, context, outputPath);
            }
        }
    }
}
