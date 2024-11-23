package org.example;

import java.io.IOException;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        try {
            Scanner sc = new Scanner(System.in);
            Config config = ConfigReader.readConfig();
            GitHubAPIClient client = new GitHubAPIClient(config);

            String repoName = selectRepository(client, sc);
            String fileName = "Hello.txt";
            String content = "Hello world";
            if (repoName == null) {
                System.out.println("No valid repository selected. Exiting.");
                return;
            }
            String newBranch = "hello-file";
            if (!createAndPrepareBranch(client, repoName, newBranch)) {
                System.out.println("Error preparing branch. Exiting.");
                return;
            }
            String fileCreationRes = client.createFile(repoName, newBranch, fileName, content, "Add Hello.txt");
            if (fileCreationRes == null) {
                System.out.println("Error creating file in repository. Exiting.");
                return;
            }else if(fileCreationRes.equals("Already exists")){
                System.out.println("File already exists. Do you want to update it? [y/n]");
                if (sc.hasNextLine()){
                    sc.nextLine();
                }
                String userResponse = sc.nextLine();
                if(userResponse.startsWith("y")){
                   if(!client.updateFile(repoName, newBranch, fileName, content, "Update Hello.txt")){
                       System.out.println("Error updating file. Exiting.");
                       return;
                   }
                    System.out.println("File updated successfully: " + fileName);
                }else{
                    System.out.println("File not updated");
                    return;
                }
            }
            if(client.doesPullRequestExist(repoName, newBranch)){
                System.out.println("Pull request already exists. Exiting.");
                return;
            }

            if (!client.createPullReq(repoName, newBranch)) {
                System.out.println("Error creating pull request. Exiting.");
            } else {
                System.out.println("Pull request created successfully!");
            }

        } catch (IOException e) {
            System.err.println("Unexpected error: " + e.getMessage());
        }
    }

    private static String selectRepository(GitHubAPIClient client, Scanner sc) throws IOException {
        String[] repos = client.listRepos();
        if (repos.length == 0) {
            System.out.println("No repositories found in your account.");
            return null;
        }

        System.out.println("Available repositories:");
        for (int i = 0; i < repos.length; i++) {
            System.out.println((i + 1) + ") " + repos[i]);
        }

        System.out.println("Select a repository by typing its number:");
        if(sc.hasNextInt()) {
            int userChoice = sc.nextInt();
            if (userChoice < 1 || userChoice > repos.length) {
                System.out.println("Invalid choice. Please restart the program and try again.");
                return null;
            }
            return repos[userChoice - 1];
        } else{
            System.out.println("Invalid input. Please enter a valid number next time.");
            return null;
        }
    }

    private static boolean createAndPrepareBranch(GitHubAPIClient client, String repoName, String newBranch) throws IOException {
        String baseBranch = client.defaultBranch(repoName);
        if (baseBranch == null) {
            System.out.println("Default branch not found for repository " + repoName + ".");
            return false;
        }

        String baseBranchSha = client.getLatestCommitSha(repoName, baseBranch);
        if (baseBranchSha == null) {
            System.out.println("Unable to retrieve latest commit SHA for branch " + baseBranch + ".");
            return false;
        }

        if (!client.createBranch(repoName, newBranch, baseBranchSha)) {
            System.out.println("Error creating new branch " + newBranch + ".");
            return false;
        }

        return true;
    }
}
